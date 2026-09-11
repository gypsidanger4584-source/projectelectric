package com.evcharge.reservation.ev.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 예약 API 컨트롤러 공통 입력 검증 (HTTP 계약·메시지 유지).
 */
final class ReservationApiSupport {

	private ReservationApiSupport() {
	}

	static void requireUserId(Long userId) {
		if (userId == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId는 필수입니다.");
		}
	}

	static void requireUserIdAndChargerId(Long userId, Long chargerId) {
		if (userId == null || chargerId == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId와 chargerId는 필수입니다.");
		}
	}
}
