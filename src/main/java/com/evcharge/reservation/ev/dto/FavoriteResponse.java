package com.evcharge.reservation.ev.dto;

public record FavoriteResponse(
		Long id,
		Long userId,
		Long stationId,
		String stationName,
		String stationAddress,
		Double latitude,
		Double longitude
) {
}
