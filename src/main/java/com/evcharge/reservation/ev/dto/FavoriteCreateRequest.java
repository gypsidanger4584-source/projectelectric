package com.evcharge.reservation.ev.dto;

public record FavoriteCreateRequest(
		Long userId,
		Long stationId
) {
}
