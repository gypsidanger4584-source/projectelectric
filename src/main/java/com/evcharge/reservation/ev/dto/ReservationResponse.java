package com.evcharge.reservation.ev.dto;

import java.time.LocalDateTime;

import com.evcharge.reservation.ev.domain.Reservation;
import com.evcharge.reservation.ev.domain.ReservationStatus;
import com.evcharge.reservation.ev.domain.ReservationType;

public record ReservationResponse(
		Long id,
		Long userId,
		Long chargerId,
		Long stationId,
		String stationName,
		LocalDateTime startTime,
		LocalDateTime endTime,
		ReservationStatus status,
		ReservationType type,
		LocalDateTime holdExpiresAt
) {

	public static ReservationResponse from(Reservation reservation) {
		Long stationId = null;
		String stationName = null;
		if (reservation.getCharger() != null && reservation.getCharger().getStation() != null) {
			stationId = reservation.getCharger().getStation().getId();
			stationName = reservation.getCharger().getStation().getName();
		}
		return new ReservationResponse(
				reservation.getId(),
				reservation.getUser().getId(),
				reservation.getCharger().getId(),
				stationId,
				stationName,
				reservation.getStartTime(),
				reservation.getEndTime(),
				reservation.getStatus(),
				reservation.getType(),
				reservation.getHoldExpiresAt()
		);
	}
}
