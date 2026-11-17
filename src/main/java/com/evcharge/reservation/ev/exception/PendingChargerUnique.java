package com.evcharge.reservation.ev.exception;

public final class PendingChargerUnique {

	public static final String INDEX_NAME = "uk_charger_one_pending";

	private PendingChargerUnique() {
	}

	public static boolean isViolation(Throwable ex) {
		for (Throwable cursor = ex; cursor != null; cursor = cursor.getCause()) {
			String message = cursor.getMessage();
			if (message != null && message.contains(INDEX_NAME)) {
				return true;
			}
		}
		return false;
	}
}
