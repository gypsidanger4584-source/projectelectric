package com.evcharge.reservation.ev.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.evcharge.reservation.ev.dto.AutoReservationRequest;
import com.evcharge.reservation.ev.dto.ReservationResponse;
import com.evcharge.reservation.ev.service.AutoReservationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class AutoReservationController {

	private final AutoReservationService autoReservationService;

	@PostMapping("/auto")
	@ResponseStatus(HttpStatus.CREATED)
	public ReservationResponse createAuto(@RequestBody AutoReservationRequest request) {
		ReservationApiSupport.requireUserId(request.userId());
		return autoReservationService.autoReserve(request);
	}
}
