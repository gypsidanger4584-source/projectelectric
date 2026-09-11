package com.evcharge.reservation.ev.dto;

public record ReservationExpiryAlert(
		Long reservationId,
		Long stationId,
		String stationName,
		String message
) {
}
