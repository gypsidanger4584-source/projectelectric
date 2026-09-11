package com.evcharge.reservation.ev.lock;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;

/**
 * 부하 테스트용 락/재시도/점유 메트릭 (Prometheus → Grafana).
 */
@Component
@RequiredArgsConstructor
public class LockMetrics {

	private final MeterRegistry registry;
	private final LockStrategyHolder strategyHolder;

	public String strategy() {
		return strategyHolder.get() == null ? "UNKNOWN" : strategyHolder.get().toUpperCase();
	}

	/** Redisson tryLock 또는 DB FOR UPDATE 대기 시간 */
	public void recordLockWait(String kind, boolean acquired, long nanos) {
		Timer.builder("ev.lock.wait")
				.description("Lock wait time (Redisson tryLock or DB FOR UPDATE)")
				.tag("strategy", strategy())
				.tag("kind", kind)
				.tag("acquired", Boolean.toString(acquired))
				.publishPercentileHistogram()
				.register(registry)
				.record(nanos, TimeUnit.NANOSECONDS);
	}

	/** reserveCharger REQUIRES_NEW 구간(커넥션 점유 프록시) */
	public <T> T recordReserveHold(Supplier<T> action) {
		Timer.Sample sample = Timer.start(registry);
		try {
			return action.get();
		} finally {
			sample.stop(Timer.builder("ev.reserve.hold")
					.description("reserveCharger critical section duration (proxy for pool occupancy)")
					.tag("strategy", strategy())
					.publishPercentileHistogram()
					.register(registry));
		}
	}

	public void recordAutoOutcome(String result) {
		Counter.builder("ev.auto_reserve.outcome")
				.description("Auto-reserve final outcome")
				.tag("strategy", strategy())
				.tag("result", result)
				.register(registry)
				.increment();
	}

	public void recordAutoConflictAttempt() {
		Counter.builder("ev.auto_reserve.conflict_attempt")
				.description("409 CONFLICT while trying a candidate (triggers fallback)")
				.tag("strategy", strategy())
				.register(registry)
				.increment();
	}
}
