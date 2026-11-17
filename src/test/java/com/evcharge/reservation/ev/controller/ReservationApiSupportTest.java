package com.evcharge.reservation.ev.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.evcharge.reservation.ev.exception.ForbiddenException;
import com.evcharge.reservation.ev.security.LoginUser;

class ReservationApiSupportTest {

	private final LoginUser loginUser = new LoginUser(7L, "a@test.com", "pw");

	@Test
	void userId를_안_보내면_로그인_유저_id를_쓴다() {
		assertThat(ReservationApiSupport.resolveUserId(loginUser, null)).isEqualTo(7L);
	}

	@Test
	void 본인_userId면_통과한다() {
		assertThat(ReservationApiSupport.resolveUserId(loginUser, 7L)).isEqualTo(7L);
	}

	@Test
	void 남의_userId면_403() {
		assertThatThrownBy(() -> ReservationApiSupport.resolveUserId(loginUser, 8L))
				.isInstanceOf(ForbiddenException.class);
	}

	@Test
	void 로그인_정보가_없으면_403() {
		assertThatThrownBy(() -> ReservationApiSupport.resolveUserId(null, 7L))
				.isInstanceOf(ForbiddenException.class);
	}
}
