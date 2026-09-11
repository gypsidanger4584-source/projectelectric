package com.evcharge.reservation.ev.dto;

import com.evcharge.reservation.ev.domain.ChargingStation;

public record ChargingStationResponse(
		Long id,
		String name,
		String address,
		Double latitude,
		Double longitude
) {

	public static ChargingStationResponse from(ChargingStation station) {
		return new ChargingStationResponse(
				station.getId(),
				station.getName(),
				station.getAddress(),
				station.getLatitude(),
				station.getLongitude()
		);
	}
}
