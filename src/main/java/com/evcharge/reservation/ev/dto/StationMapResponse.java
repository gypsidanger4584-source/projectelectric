package com.evcharge.reservation.ev.dto;

public record StationMapResponse(
		Long id,
		String statId,
		String name,
		Double latitude,
		Double longitude,
		String kind,
		String kindDetail,
		String parkingFree,
		String limitYn,
		Integer fastAvailable,
		Integer slowAvailable,
		boolean isMyChargingMatch,
		boolean isExternallyOpen
) {
}
