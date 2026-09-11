package com.evcharge.reservation.ev.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.evcharge.reservation.ev.dto.ChargingStationResponse;
import com.evcharge.reservation.ev.dto.StationDetailResponse;
import com.evcharge.reservation.ev.dto.StationMapResponse;
import com.evcharge.reservation.ev.service.ChargingStationService;
import com.evcharge.reservation.ev.service.ChargingStationSyncService;
import com.evcharge.reservation.ev.service.StationMapQueryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/stations")
@RequiredArgsConstructor
public class ChargingStationController {

	private final ChargingStationService chargingStationService;
	private final ChargingStationSyncService chargingStationSyncService;
	private final StationMapQueryService stationMapQueryService;

	@GetMapping
	public List<ChargingStationResponse> findAll() {
		return chargingStationService.findAll();
	}

	@GetMapping("/nearby")
	public List<StationMapResponse> nearby(
			@RequestParam double lat,
			@RequestParam double lng,
			@RequestParam(required = false) Double radius,
			@RequestParam String vehicleBrand,
			@RequestParam String vehicleModel,
			@RequestParam(defaultValue = "false") boolean showExternalOpen
	) {
		return stationMapQueryService.findNearby(lat, lng, radius, vehicleBrand, vehicleModel, showExternalOpen);
	}

	@PostMapping("/refresh")
	public ResponseEntity<Map<String, String>> refresh(
			@RequestParam(value = "lat", required = false, defaultValue = "37.566826") String lat,
			@RequestParam(value = "lng", required = false, defaultValue = "126.9786567") String lng,
			@RequestParam(value = "radius", required = false, defaultValue = "3") String radius
	) {
		return doRefresh(lat, lng, radius);
	}

	/** 임시 디버깅/적재용 — CSRF 우회 테스트 */
	@GetMapping("/refresh")
	public ResponseEntity<Map<String, String>> refreshStationsGet(
			@RequestParam(value = "lat", required = false, defaultValue = "37.566826") String lat,
			@RequestParam(value = "lng", required = false, defaultValue = "126.9786567") String lng,
			@RequestParam(value = "radius", required = false, defaultValue = "3") String radius
	) {
		return doRefresh(lat, lng, radius);
	}

	private ResponseEntity<Map<String, String>> doRefresh(String lat, String lng, String radius) {
		double parsedLat;
		double parsedLng;
		double parsedRadius;
		try {
			parsedLat = Double.parseDouble(lat.trim());
			parsedLng = Double.parseDouble(lng.trim());
			parsedRadius = Double.parseDouble(radius.trim());
		} catch (NullPointerException | NumberFormatException e) {
			throw new IllegalArgumentException(
					"lat/lng/radius 형식이 올바르지 않습니다. lat=" + lat + ", lng=" + lng + ", radius=" + radius);
		}

		int updated = chargingStationSyncService.syncByRadius(parsedLat, parsedLng, parsedRadius);
		return ResponseEntity.ok(Map.of(
				"status", "ok",
				"lat", String.valueOf(parsedLat),
				"lng", String.valueOf(parsedLng),
				"radiusKm", String.valueOf(parsedRadius),
				"updatedStations", String.valueOf(updated)
		));
	}

	@GetMapping("/{stationId}/detail")
	public StationDetailResponse stationDetail(
			@PathVariable Long stationId,
			@RequestParam(required = false) Long userId
	) {
		return chargingStationService.getStationDetail(stationId, userId);
	}

	@GetMapping("/{id}")
	public ChargingStationResponse findById(@PathVariable Long id) {
		return chargingStationService.findById(id);
	}
}
