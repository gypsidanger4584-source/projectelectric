package com.evcharge.reservation.ev.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;

class AuthRateLimitServiceRefreshTest {

	@Test
	void 사용자당_분당_5회까지만_허용하고_첫_호출에서만_만료를_건다() {
		RedissonClient redisson = mock(RedissonClient.class);
		RAtomicLong counter = mock(RAtomicLong.class);
		when(redisson.getAtomicLong("api:refresh:user:7")).thenReturn(counter);
		when(counter.incrementAndGet()).thenReturn(1L, 2L, 3L, 4L, 5L, 6L);

		AuthRateLimitService service = new AuthRateLimitService(redisson);

		for (int i = 1; i <= 5; i++) {
			assertThat(service.tryConsumeRefresh(7L)).as("%d번째", i).isTrue();
		}
		assertThat(service.tryConsumeRefresh(7L)).as("6번째").isFalse();
		verify(counter).expire(AuthRateLimitService.REFRESH_WINDOW);
	}

	@Test
	void 로그인_정보가_없으면_거부한다() {
		AuthRateLimitService service = new AuthRateLimitService(mock(RedissonClient.class));

		assertThat(service.tryConsumeRefresh(null)).isFalse();
	}
}
