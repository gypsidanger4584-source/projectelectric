package com.evcharge.reservation.ev.dto;

public record UserSettingUpdateRequest(
		String speedPriority,
		String parking,
		String place,
		Boolean externalOpenOnly,
		Double maxDistanceKm
) {
}
