package com.evcharge.reservation.ev.dto;

import com.evcharge.reservation.ev.domain.ChargerStatus;
import com.evcharge.reservation.ev.domain.ChargerType;

public record ChargerCreateRequest(
		ChargerType chargerType,
		ChargerStatus status
) {
}
