package com.evcharge.reservation.ev.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.evcharge.reservation.ev.domain.Charger;
import com.evcharge.reservation.ev.domain.ChargerStatus;
import com.evcharge.reservation.ev.domain.Reservation;
import com.evcharge.reservation.ev.domain.ReservationHoldPolicy;
import com.evcharge.reservation.ev.domain.ReservationStatus;
import com.evcharge.reservation.ev.domain.ReservationType;
import com.evcharge.reservation.ev.domain.User;
import com.evcharge.reservation.ev.dto.ReservationCreateRequest;
import com.evcharge.reservation.ev.dto.ReservationExpiryAlert;
import com.evcharge.reservation.ev.dto.ReservationResponse;
import com.evcharge.reservation.ev.lock.LockStrategyResolver;
import com.evcharge.reservation.ev.repository.ChargerRepository;
import com.evcharge.reservation.ev.repository.ReservationRepository;
import com.evcharge.reservation.ev.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

	private final ReservationRepository reservationRepository;
	private final UserRepository userRepository;
	private final ChargerRepository chargerRepository;
	private final LockStrategyResolver lockStrategyResolver;

	@Transactional
	public ReservationResponse create(ReservationCreateRequest request) {
		User user = userRepository.findById(request.userId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "유저를 찾을 수 없습니다."));
		Charger charger = chargerRepository.findById(request.chargerId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "충전기를 찾을 수 없습니다."));

		Reservation reservation = Reservation.builder()
				.user(user)
				.charger(charger)
				.startTime(request.startTime())
				.endTime(request.endTime())
				.status(ReservationStatus.PENDING)
				.type(ReservationType.MANUAL)
				.holdExpiresAt(request.endTime())
				.expiryNotified(false)
				.build();

		return ReservationResponse.from(reservationRepository.save(reservation));
	}

	/**
	 * 런타임 락 전략(비관적 / Redisson)으로 충전기 점유 후 PENDING 예약 생성.
	 * REQUIRES_NEW: 자동예약의 긴 후보조회 TX와 분리해, 락/상태검증이 stale 엔티티를 보지 않게 한다.
	 *
	 * @param originLat 예약 요청 출발 위도(현재 미사용, API 호환·추후 감사 로그용으로 유지)
	 * @param originLng 예약 요청 출발 경도(현재 미사용, API 호환·추후 감사 로그용으로 유지)
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public ReservationResponse reserveCharger(
			Long chargerId,
			Long userId,
			ReservationType type,
			Double originLat,
			Double originLng
	) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "유저를 찾을 수 없습니다."));

		return lockStrategyResolver.resolve().executeWithLock(chargerId, () -> {
			// 락 획득 후 반드시 DB에서 다시 읽는다 (FOR UPDATE). 후보조회 L1 캐시를 쓰지 않는다.
			Charger charger = chargerRepository.findByIdForUpdate(chargerId)
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "충전기를 찾을 수 없습니다."));

			if (reservationRepository.existsByUserIdAndChargerIdAndStatus(
					userId, chargerId, ReservationStatus.PENDING)) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 예약되었습니다");
			}

			if (reservationRepository.existsByCharger_IdAndStatus(chargerId, ReservationStatus.PENDING)) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 예약되었습니다");
			}

			if (charger.getStatus() != ChargerStatus.AVAILABLE) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 예약되었습니다");
			}

			LocalDateTime now = LocalDateTime.now();
			ReservationType resolvedType = type == null ? ReservationType.MANUAL : type;
			LocalDateTime holdExpiresAt = ReservationHoldPolicy.holdExpiresAt(now, resolvedType);

			Reservation reservation = Reservation.builder()
					.user(user)
					.charger(charger)
					.startTime(now)
					.endTime(holdExpiresAt)
					.holdExpiresAt(holdExpiresAt)
					.status(ReservationStatus.PENDING)
					.type(resolvedType)
					.expiryNotified(false)
					.build();

			charger.markStatus(ChargerStatus.RESERVED);
			chargerRepository.save(charger);
			return ReservationResponse.from(reservationRepository.save(reservation));
		});
	}

	public ReservationResponse findById(Long id) {
		return ReservationResponse.from(findReservation(id));
	}

	public List<ReservationResponse> findByUserId(Long userId) {
		if (!userRepository.existsById(userId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "유저를 찾을 수 없습니다.");
		}
		return reservationRepository.findByUserId(userId).stream()
				.map(ReservationResponse::from)
				.toList();
	}

	@Transactional
	public ReservationResponse cancel(Long id, Long userId) {
		if (userId == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId는 필수입니다.");
		}
		Reservation reservation = reservationRepository.findByIdAndUserId(id, userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "예약을 찾을 수 없습니다."));
		if (reservation.getStatus() != ReservationStatus.PENDING) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "취소할 수 없는 예약 상태입니다.");
		}
		reservation.cancel();
		releaseChargerIfReserved(reservation.getCharger());
		return ReservationResponse.from(reservation);
	}

	/**
	 * holdExpiresAt이 지난 PENDING 예약을 EXPIRED로 바꾸고 충전기를 반환한다.
	 */
	@Transactional
	public int expireOverdueReservations() {
		List<Reservation> overdue = reservationRepository.findExpiredPending(
				ReservationStatus.PENDING,
				LocalDateTime.now()
		);
		int count = 0;
		for (Reservation reservation : overdue) {
			reservation.expire();
			releaseChargerIfReserved(reservation.getCharger());
			count++;
			log.info("Reservation {} expired for user {}", reservation.getId(), reservation.getUser().getId());
		}
		return count;
	}

	/**
	 * 만료 알림을 조회하고 읽음 처리한다.
	 */
	@Transactional
	public List<ReservationExpiryAlert> consumeExpiryAlerts(Long userId) {
		if (userId == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId는 필수입니다.");
		}
		if (!userRepository.existsById(userId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "유저를 찾을 수 없습니다.");
		}
		List<Reservation> list = reservationRepository.findUnnotifiedExpiredByUserId(
				userId,
				ReservationStatus.EXPIRED
		);
		List<ReservationExpiryAlert> alerts = new ArrayList<>(list.size());
		for (Reservation reservation : list) {
			String stationName = reservation.getCharger().getStation().getName();
			alerts.add(new ReservationExpiryAlert(
					reservation.getId(),
					reservation.getCharger().getStation().getId(),
					stationName,
					"미도착으로 예약이 만료되었습니다: " + (stationName == null ? "충전소" : stationName)
			));
			reservation.markExpiryNotified();
		}
		return alerts;
	}

	private void releaseChargerIfReserved(Charger charger) {
		if (charger != null && charger.getStatus() == ChargerStatus.RESERVED) {
			charger.markStatus(ChargerStatus.AVAILABLE);
			chargerRepository.save(charger);
		}
	}

	private Reservation findReservation(Long id) {
		return reservationRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "예약을 찾을 수 없습니다."));
	}
}
