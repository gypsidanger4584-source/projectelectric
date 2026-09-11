package com.evcharge.reservation.ev.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.evcharge.reservation.ev.dto.ReservationCreateRequest;
import com.evcharge.reservation.ev.dto.ReservationExpiryAlert;
import com.evcharge.reservation.ev.dto.ReservationResponse;
import com.evcharge.reservation.ev.service.ReservationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

	private final ReservationService reservationService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ReservationResponse create(@RequestBody ReservationCreateRequest request) {
		return reservationService.create(request);
	}

	@GetMapping("/{id}")
	public ReservationResponse findById(@PathVariable Long id) {
		return reservationService.findById(id);
	}

	@GetMapping("/user/{userId}")
	public List<ReservationResponse> findByUserId(@PathVariable Long userId) {
		return reservationService.findByUserId(userId);
	}

	@GetMapping("/user/{userId}/expiry-alerts")
	public List<ReservationExpiryAlert> expiryAlerts(@PathVariable Long userId) {
		return reservationService.consumeExpiryAlerts(userId);
	}

	@DeleteMapping("/{id}")
	public ReservationResponse cancel(
			@PathVariable Long id,
			@RequestParam Long userId
	) {
		return reservationService.cancel(id, userId);
	}
}
