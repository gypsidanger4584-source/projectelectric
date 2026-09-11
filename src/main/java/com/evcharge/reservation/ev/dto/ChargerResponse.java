package com.evcharge.reservation.ev.dto;

import com.evcharge.reservation.ev.domain.Charger;
import com.evcharge.reservation.ev.domain.ChargerStatus;
import com.evcharge.reservation.ev.domain.ChargerType;

public record ChargerResponse(
		Long id,
		ChargerType chargerType,
		ChargerStatus status
) {

	public static ChargerResponse from(Charger charger) {
		return new ChargerResponse(
				charger.getId(),
				charger.getChargerType(),
				charger.getStatus()
		);
	}
}
