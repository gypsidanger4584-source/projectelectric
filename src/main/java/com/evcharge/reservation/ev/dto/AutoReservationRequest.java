package com.evcharge.reservation.ev.dto;

public record AutoReservationRequest(
		Long userId,
		double lat,
		double lng,
		Double maxDistanceKm,
		String speed,
		String parking,
		String place,
		Boolean externalOpenOnly
) {
}
