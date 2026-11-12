package com.evcharge.reservation.ev.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.evcharge.reservation.ev.domain.Charger;
import com.evcharge.reservation.ev.domain.ChargerStatus;
import com.evcharge.reservation.ev.domain.Reservation;
import com.evcharge.reservation.ev.domain.ReservationStatus;
import com.evcharge.reservation.ev.domain.User;
import com.evcharge.reservation.ev.dto.ReservationResponse;
import com.evcharge.reservation.ev.exception.ConflictException;
import com.evcharge.reservation.ev.lock.LockMetrics;
import com.evcharge.reservation.ev.lock.LockStrategyResolver;
import com.evcharge.reservation.ev.repository.ChargerRepository;
import com.evcharge.reservation.ev.repository.ReservationRepository;
import com.evcharge.reservation.ev.repository.UserRepository;

/**
 * 취소는 만료와 같은 조건부 UPDATE(CAS)로 처리한다.
 * 1행이면 충전기 반환, 0행이면 충전기를 건드리지 않고 409.
 */
class ReservationServiceCancelTest {

	private static final Long RESERVATION_ID = 10L;
	private static final Long USER_ID = 7L;
	private static final Long CHARGER_ID = 5L;

	private ReservationRepository reservationRepository;
	private ChargerRepository chargerRepository;
	private ReservationService service;

	@BeforeEach
	void setUp() {
		reservationRepository = mock(ReservationRepository.class);
		chargerRepository = mock(ChargerRepository.class);
		service = new ReservationService(
				reservationRepository,
				mock(UserRepository.class),
				chargerRepository,
				mock(LockStrategyResolver.class),
				mock(LockMetrics.class)
		);
	}

	private Charger charger(ChargerStatus status) {
		return Charger.builder().id(CHARGER_ID).status(status).statCode(2).build();
	}

	private Reservation reservation(ReservationStatus status, Charger charger) {
		return Reservation.builder()
				.id(RESERVATION_ID)
				.user(User.builder().id(USER_ID).build())
				.charger(charger)
				.startTime(LocalDateTime.now())
				.endTime(LocalDateTime.now().plusMinutes(10))
				.status(status)
				.build();
	}

	@Test
	void 조건부_UPDATE가_1행이면_충전기를_반환하고_취소_상태를_돌려준다() {
		Charger charger = charger(ChargerStatus.RESERVED);
		when(reservationRepository.findByIdAndUserId(RESERVATION_ID, USER_ID))
				.thenReturn(Optional.of(reservation(ReservationStatus.PENDING, charger)))
				.thenReturn(Optional.of(reservation(ReservationStatus.CANCELLED, charger)));
		when(reservationRepository.cancelIfPending(
				RESERVATION_ID, USER_ID, ReservationStatus.CANCELLED, ReservationStatus.PENDING)).thenReturn(1);
		when(chargerRepository.findByIdForUpdate(CHARGER_ID)).thenReturn(Optional.of(charger));

		ReservationResponse response = service.cancel(RESERVATION_ID, USER_ID);

		assertThat(response.status()).isEqualTo(ReservationStatus.CANCELLED);
		assertThat(charger.getStatus()).isEqualTo(ChargerStatus.AVAILABLE);
		verify(chargerRepository).save(charger);
	}

	@Test
	void 읽은_뒤_만료가_먼저_끝나_0행이면_409이고_충전기는_건드리지_않는다() {
		Charger charger = charger(ChargerStatus.AVAILABLE);
		when(reservationRepository.findByIdAndUserId(RESERVATION_ID, USER_ID))
				.thenReturn(Optional.of(reservation(ReservationStatus.PENDING, charger)));
		when(reservationRepository.cancelIfPending(
				RESERVATION_ID, USER_ID, ReservationStatus.CANCELLED, ReservationStatus.PENDING)).thenReturn(0);

		assertThatThrownBy(() -> service.cancel(RESERVATION_ID, USER_ID))
				.isInstanceOf(ConflictException.class)
				.hasMessage("취소할 수 없는 예약 상태입니다.");

		verify(chargerRepository, never()).findByIdForUpdate(any());
		verify(chargerRepository, never()).save(any());
	}

	@Test
	void 이미_PENDING이_아니면_UPDATE를_시도하지_않고_409() {
		Charger charger = charger(ChargerStatus.AVAILABLE);
		when(reservationRepository.findByIdAndUserId(RESERVATION_ID, USER_ID))
				.thenReturn(Optional.of(reservation(ReservationStatus.EXPIRED, charger)));

		assertThatThrownBy(() -> service.cancel(RESERVATION_ID, USER_ID))
				.isInstanceOf(ConflictException.class);

		verify(reservationRepository, never()).cancelIfPending(any(), any(), any(), any());
	}

	@Test
	void 충전기가_이미_다른_상태면_덮어쓰지_않는다() {
		Charger charger = charger(ChargerStatus.CHARGING);
		when(reservationRepository.findByIdAndUserId(RESERVATION_ID, USER_ID))
				.thenReturn(Optional.of(reservation(ReservationStatus.PENDING, charger)))
				.thenReturn(Optional.of(reservation(ReservationStatus.CANCELLED, charger)));
		when(reservationRepository.cancelIfPending(
				eq(RESERVATION_ID), eq(USER_ID), eq(ReservationStatus.CANCELLED), eq(ReservationStatus.PENDING)))
				.thenReturn(1);
		when(chargerRepository.findByIdForUpdate(CHARGER_ID)).thenReturn(Optional.of(charger));

		service.cancel(RESERVATION_ID, USER_ID);

		assertThat(charger.getStatus()).isEqualTo(ChargerStatus.CHARGING);
		verify(chargerRepository, never()).save(any());
	}
}
