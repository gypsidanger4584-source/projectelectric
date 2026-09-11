package com.evcharge.reservation.ev.lock;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

/**
 * DB 행 락은 reserveCharger 내부 findByIdForUpdate에서 수행한다.
 * 이 전략 빈은 런타임 토글(비관적 경로) 식별용이며, 추가 Redis 락은 걸지 않는다.
 */
@Component("pessimisticLockStrategy")
public class PessimisticLockStrategy implements ChargerLockStrategy {

	@Override
	public <T> T executeWithLock(Long chargerId, Supplier<T> action) {
		return action.get();
	}
}
