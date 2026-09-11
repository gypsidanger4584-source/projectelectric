package com.evcharge.reservation.ev.lock;

import org.springframework.stereotype.Component;

@Component
public class LockStrategyHolder {

	private volatile String current = "PESSIMISTIC";

	public String get() {
		return current;
	}

	public void set(String value) {
		if (value == null || value.isBlank()) {
			return;
		}
		this.current = value.trim().toUpperCase();
	}
}
