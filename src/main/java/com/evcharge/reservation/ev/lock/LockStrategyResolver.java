package com.evcharge.reservation.ev.lock;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LockStrategyResolver {

	private final Map<String, ChargerLockStrategy> strategies;
	private final LockStrategyHolder holder;

	public ChargerLockStrategy resolve() {
		ChargerLockStrategy strategy = "REDISSON".equalsIgnoreCase(holder.get())
				? strategies.get("redissonLockStrategy")
				: strategies.get("pessimisticLockStrategy");
		if (strategy == null) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "락 전략 빈을 찾을 수 없습니다.");
		}
		return strategy;
	}
}
