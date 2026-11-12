package com.evcharge.reservation.ev.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.evcharge.reservation.ev.dto.ChargerResponse;
import com.evcharge.reservation.ev.service.ChargerService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/stations/{stationId}/chargers")
@RequiredArgsConstructor
public class ChargerController {

	private final ChargerService chargerService;

	@GetMapping
	public List<ChargerResponse> findByStationId(@PathVariable Long stationId) {
		return chargerService.findByStationId(stationId);
	}
}
