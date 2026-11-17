package com.evcharge.reservation.ev.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.evcharge.reservation.ev.domain.ReservationType;
import com.evcharge.reservation.ev.dto.ManualReservationRequest;
import com.evcharge.reservation.ev.dto.ReservationResponse;
import com.evcharge.reservation.ev.security.LoginUser;
import com.evcharge.reservation.ev.service.ReservationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ManualReservationController {

	private final ReservationService reservationService;

	@PostMapping("/manual")
	@ResponseStatus(HttpStatus.CREATED)
	public ReservationResponse createManual(
			@RequestBody ManualReservationRequest request,
			@AuthenticationPrincipal LoginUser loginUser
	) {
		Long userId = ReservationApiSupport.resolveUserId(loginUser, request.userId());
		ReservationApiSupport.requireChargerId(request.chargerId());
		return reservationService.reserveCharger(
				request.chargerId(),
				userId,
				ReservationType.MANUAL,
				null,
				null
		);
	}
}
