package com.evcharge.reservation.ev.lock;

import java.util.function.Supplier;

public interface ChargerLockStrategy {

	<T> T executeWithLock(Long chargerId, Supplier<T> action);
}
