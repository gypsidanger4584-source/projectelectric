package com.evcharge.reservation.ev.dto;

public record UserSettingResponse(
		Long userId,
		String speedPriority,
		String parking,
		String place,
		boolean externalOpenOnly,
		double maxDistanceKm
) {
}
