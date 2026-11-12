package com.evcharge.reservation.ev.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.evcharge.reservation.ev.dto.ReservationExpiryAlert;
import com.evcharge.reservation.ev.dto.ReservationResponse;
import com.evcharge.reservation.ev.security.LoginUser;
import com.evcharge.reservation.ev.service.ReservationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

	private final ReservationService reservationService;

	@GetMapping("/{id}")
	public ReservationResponse findById(
			@PathVariable Long id,
			@AuthenticationPrincipal LoginUser loginUser
	) {
		return reservationService.findByIdAndUserId(id, ReservationApiSupport.resolveUserId(loginUser, null));
	}

	@GetMapping("/user/{userId}")
	public List<ReservationResponse> findByUserId(
			@PathVariable Long userId,
			@AuthenticationPrincipal LoginUser loginUser
	) {
		return reservationService.findByUserId(ReservationApiSupport.resolveUserId(loginUser, userId));
	}

	@GetMapping("/user/{userId}/expiry-alerts")
	public List<ReservationExpiryAlert> expiryAlerts(
			@PathVariable Long userId,
			@AuthenticationPrincipal LoginUser loginUser
	) {
		return reservationService.consumeExpiryAlerts(ReservationApiSupport.resolveUserId(loginUser, userId));
	}

	@DeleteMapping("/{id}")
	public ReservationResponse cancel(
			@PathVariable Long id,
			@RequestParam(required = false) Long userId,
			@AuthenticationPrincipal LoginUser loginUser
	) {
		return reservationService.cancel(id, ReservationApiSupport.resolveUserId(loginUser, userId));
	}
}
