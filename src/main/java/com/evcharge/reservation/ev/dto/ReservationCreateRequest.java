package com.evcharge.reservation.ev.dto;

import java.time.LocalDateTime;

public record ReservationCreateRequest(
		Long userId,
		Long chargerId,
		LocalDateTime startTime,
		LocalDateTime endTime
) {
}
