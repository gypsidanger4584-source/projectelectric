package com.evcharge.reservation.ev.lock;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

@Component("redissonLockStrategy")
@RequiredArgsConstructor
public class RedissonLockStrategy implements ChargerLockStrategy {

	private final RedissonClient redissonClient;
	private final LockMetrics lockMetrics;

	@Override
	public <T> T executeWithLock(Long chargerId, Supplier<T> action) {
		RLock lock = redissonClient.getLock("charger-lock:" + chargerId);
		boolean acquired;
		long waitStart = System.nanoTime();
		try {
			acquired = lock.tryLock(3, 5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			lockMetrics.recordLockWait("redisson", false, System.nanoTime() - waitStart);
			Thread.currentThread().interrupt();
			throw new ResponseStatusException(HttpStatus.CONFLICT, "충전기 락 획득이 중단되었습니다.");
		}
		lockMetrics.recordLockWait("redisson", acquired, System.nanoTime() - waitStart);
		if (!acquired) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 예약 처리 중인 충전기입니다.");
		}

		boolean unlockRegistered = false;
		try {
			if (TransactionSynchronizationManager.isSynchronizationActive()) {
				TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
					@Override
					public void afterCompletion(int status) {
						unlockQuietly(lock);
					}
				});
				unlockRegistered = true;
			}
			return action.get();
		} catch (RuntimeException | Error e) {
			if (!unlockRegistered) {
				unlockQuietly(lock);
			}
			throw e;
		} finally {
			if (!unlockRegistered) {
				unlockQuietly(lock);
			}
		}
	}

	private static void unlockQuietly(RLock lock) {
		try {
			if (lock.isHeldByCurrentThread()) {
				lock.unlock();
			}
		} catch (Exception ignored) {
			// lease 만료 등으로 unlock 실패해도 예약 TX 결과는 유지
		}
	}
}
