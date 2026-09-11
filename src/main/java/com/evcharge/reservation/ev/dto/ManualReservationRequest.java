package com.evcharge.reservation.ev.dto;

public record ManualReservationRequest(
		Long userId,
		Long chargerId
) {
}
