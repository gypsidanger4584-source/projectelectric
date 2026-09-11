package com.evcharge.reservation.ev.domain;

import java.time.LocalDateTime;

/**
 * 예약 유형별 홀드(점유 유지) 시간 정책.
 * AUTO=10분, MANUAL=15분 — 기존 ReservationService 상수와 동일.
 */
public final class ReservationHoldPolicy {

	public static final int MANUAL_HOLD_MINUTES = 15;
	public static final int AUTO_HOLD_MINUTES = 10;

	private ReservationHoldPolicy() {
	}

	public static int holdMinutes(ReservationType type) {
		ReservationType resolved = type == null ? ReservationType.MANUAL : type;
		return resolved == ReservationType.AUTO ? AUTO_HOLD_MINUTES : MANUAL_HOLD_MINUTES;
	}

	public static LocalDateTime holdExpiresAt(LocalDateTime now, ReservationType type) {
		return now.plusMinutes(holdMinutes(type));
	}
}
