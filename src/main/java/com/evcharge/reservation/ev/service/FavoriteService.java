package com.evcharge.reservation.ev.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.evcharge.reservation.ev.domain.Charger;
import com.evcharge.reservation.ev.domain.ChargerStatus;
import com.evcharge.reservation.ev.domain.ChargingStation;
import com.evcharge.reservation.ev.domain.Favorite;
import com.evcharge.reservation.ev.domain.ReservationType;
import com.evcharge.reservation.ev.domain.User;
import com.evcharge.reservation.ev.dto.FavoriteResponse;
import com.evcharge.reservation.ev.dto.ReservationResponse;
import com.evcharge.reservation.ev.dto.UserSettingResponse;
import com.evcharge.reservation.ev.repository.ChargerRepository;
import com.evcharge.reservation.ev.repository.ChargingStationRepository;
import com.evcharge.reservation.ev.repository.FavoriteRepository;
import com.evcharge.reservation.ev.repository.UserRepository;
import com.evcharge.reservation.ev.util.StationPreferenceMatcher;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FavoriteService {

	private final FavoriteRepository favoriteRepository;
	private final UserRepository userRepository;
	private final ChargingStationRepository chargingStationRepository;
	private final ChargerRepository chargerRepository;
	private final ReservationService reservationService;
	private final UserSettingService userSettingService;

	@Transactional
	public FavoriteResponse add(Long userId, Long stationId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "유저를 찾을 수 없습니다."));
		ChargingStation station = chargingStationRepository.findById(stationId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "충전소를 찾을 수 없습니다."));

		if (favoriteRepository.existsByUserIdAndStationId(userId, stationId)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 즐겨찾기에 등록된 충전소입니다.");
		}

		Favorite favorite = Favorite.builder()
				.user(user)
				.station(station)
				.build();
		return toResponse(favoriteRepository.save(favorite));
	}

	public List<FavoriteResponse> listByUser(Long userId) {
		if (!userRepository.existsById(userId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "유저를 찾을 수 없습니다.");
		}
		return favoriteRepository.findByUserIdWithStation(userId).stream()
				.map(this::toResponse)
				.toList();
	}

	@Transactional
	public void remove(Long userId, Long stationId) {
		Favorite favorite = favoriteRepository.findByUserIdAndStationId(userId, stationId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "즐겨찾기를 찾을 수 없습니다."));
		favoriteRepository.delete(favorite);
	}

	@Transactional
	public ReservationResponse reserveFromFavorite(Long stationId, Long userId) {
		if (!favoriteRepository.existsByUserIdAndStationId(userId, stationId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "즐겨찾기 충전소가 아닙니다.");
		}

		ChargingStation station = chargingStationRepository.findById(stationId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "충전소를 찾을 수 없습니다."));
		UserSettingResponse setting = userSettingService.getOrCreate(userId);

		if (!StationPreferenceMatcher.matchesParking(station, setting.parking())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "설정한 주차요금 조건에 맞지 않습니다.");
		}
		if (!StationPreferenceMatcher.matchesPlace(station, setting.place())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "설정한 장소 조건에 맞지 않습니다.");
		}
		if (!StationPreferenceMatcher.matchesExternalOpen(station, setting.externalOpenOnly())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "외부인 개방 조건에 맞지 않습니다.");
		}

		List<Charger> available = chargerRepository.findByStationIdAndStatus(stationId, ChargerStatus.AVAILABLE);
		if (available.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 예약되었습니다");
		}

		String prefer = setting.speedPriority() == null ? "FAST" : setting.speedPriority().toUpperCase();
		List<Charger> preferred;
		if ("ALL".equals(prefer)) {
			preferred = available.stream()
					.sorted(Comparator.comparing(Charger::getId))
					.toList();
		} else {
			preferred = available.stream()
					.filter(c -> matchesPrefer(c, prefer))
					.sorted(Comparator.comparing(Charger::getId))
					.toList();
			if (preferred.isEmpty()) {
				String label = "SLOW".equals(prefer) ? "완속" : "급속";
				throw new ResponseStatusException(
						HttpStatus.CONFLICT,
						label + " 충전기가 없습니다 확인 후 다시 시도해주세요"
				);
			}
		}

		Charger selected = preferred.get(0);

		return reservationService.reserveCharger(
				selected.getId(),
				userId,
				ReservationType.MANUAL,
				null,
				null
		);
	}

	private static boolean matchesPrefer(Charger charger, String prefer) {
		Integer output = charger.getOutput();
		if ("SLOW".equals(prefer)) {
			return output == null || output < 50;
		}
		return output != null && output >= 50;
	}

	private FavoriteResponse toResponse(Favorite favorite) {
		ChargingStation station = favorite.getStation();
		return new FavoriteResponse(
				favorite.getId(),
				favorite.getUser().getId(),
				station.getId(),
				station.getName(),
				station.getAddress(),
				station.getLatitude(),
				station.getLongitude()
		);
	}
}
