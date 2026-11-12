package com.evcharge.reservation.ev.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.evcharge.reservation.ev.exception.ForbiddenException;
import com.evcharge.reservation.ev.security.LoginUser;

/**
 * 컨트롤러 공통 입력 검증 (HTTP 계약·메시지 유지).
 */
final class ReservationApiSupport {

	private ReservationApiSupport() {
	}

	/**
	 * 요청의 userId는 신뢰하지 않고 세션 로그인 유저 id를 쓴다.
	 * 기존 클라이언트 호환을 위해 userId를 보내면 본인 id와 같아야 한다.
	 */
	static Long resolveUserId(LoginUser loginUser, Long requestedUserId) {
		if (loginUser == null) {
			throw new ForbiddenException("로그인이 필요합니다.");
		}
		if (requestedUserId != null && !requestedUserId.equals(loginUser.getId())) {
			throw new ForbiddenException("본인 정보만 요청할 수 있습니다.");
		}
		return loginUser.getId();
	}

	static void requireChargerId(Long chargerId) {
		if (chargerId == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "chargerId는 필수입니다.");
		}
	}
}
