package com.evcharge.reservation.ev.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.evcharge.reservation.ev.domain.Charger;
import com.evcharge.reservation.ev.domain.ChargerStatus;
import com.evcharge.reservation.ev.domain.ChargingStation;
import com.evcharge.reservation.ev.domain.VehicleConnectorMapping;
import com.evcharge.reservation.ev.dto.StationMapResponse;
import com.evcharge.reservation.ev.repository.ChargerRepository;
import com.evcharge.reservation.ev.repository.ChargingStationRepository;
import com.evcharge.reservation.ev.repository.VehicleConnectorMappingRepository;
import com.evcharge.reservation.ev.util.GeoUtils;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StationMapQueryService {

	private static final double DEFAULT_RADIUS_KM = 3.0;
	private static final double MAX_RADIUS_KM = 5.0;

	private final ChargingStationRepository chargingStationRepository;
	private final ChargerRepository chargerRepository;
	private final VehicleConnectorMappingRepository vehicleConnectorMappingRepository;

	public List<StationMapResponse> findNearby(
			double lat,
			double lng,
			Double radiusKm,
			String vehicleBrand,
			String vehicleModel,
			boolean showExternalOpen
	) {
		if (vehicleBrand == null || vehicleBrand.isBlank() || vehicleModel == null || vehicleModel.isBlank()) {
			throw new IllegalArgumentException("vehicleBrand와 vehicleModel은 필수입니다.");
		}

		double radius = radiusKm == null || radiusKm <= 0 ? DEFAULT_RADIUS_KM : Math.min(radiusKm, MAX_RADIUS_KM);
		Set<String> supportedCodes = vehicleConnectorMappingRepository
				.findByBrandAndModel(vehicleBrand.trim(), vehicleModel.trim())
				.stream()
				.map(VehicleConnectorMapping::getApiChgerTypeCode)
				.collect(Collectors.toCollection(HashSet::new));

		if (supportedCodes.isEmpty()) {
			return List.of();
		}

		double latDelta = radius / 111.0;
		double lngDelta = radius / (111.0 * Math.max(0.01, Math.cos(Math.toRadians(lat))));

		List<ChargingStation> candidates = chargingStationRepository.findInBoundingBox(
				lat - latDelta,
				lat + latDelta,
				lng - lngDelta,
				lng + lngDelta
		);

		List<ChargingStation> inRadius = candidates.stream()
				.filter(s -> GeoUtils.haversineKm(lat, lng, s.getLatitude(), s.getLongitude()) <= radius)
				.toList();

		if (inRadius.isEmpty()) {
			return List.of();
		}

		List<Long> stationIds = inRadius.stream().map(ChargingStation::getId).toList();
		Map<Long, List<Charger>> chargersByStation = chargerRepository.findActiveByStationIdIn(stationIds)
				.stream()
				.collect(Collectors.groupingBy(c -> c.getStation().getId()));

		List<StationMapResponse> result = new ArrayList<>();
		for (ChargingStation station : inRadius) {
			List<Charger> chargers = chargersByStation.getOrDefault(station.getId(), List.of());
			boolean myMatch = chargers.stream()
					.anyMatch(c -> supportedCodes.contains(c.getApiChgerTypeCode()));
			boolean externallyOpen = "N".equalsIgnoreCase(station.getLimitYn());

			if (!myMatch && !(showExternalOpen && externallyOpen)) {
				continue;
			}

			int fastAvailable = 0;
			int slowAvailable = 0;
			for (Charger charger : chargers) {
				if (charger.getStatus() != ChargerStatus.AVAILABLE) {
					continue;
				}
				Integer output = charger.getOutput();
				if (output != null && output >= 50) {
					fastAvailable++;
				} else if (output != null && output >= 3 && output <= 7) {
					slowAvailable++;
				} else if ("02".equals(charger.getApiChgerTypeCode()) || "08".equals(charger.getApiChgerTypeCode())) {
					slowAvailable++;
				} else if (output != null && output > 7) {
					fastAvailable++;
				}
			}

			result.add(new StationMapResponse(
					station.getId(),
					station.getStatId(),
					station.getName(),
					station.getLatitude(),
					station.getLongitude(),
					station.getKind(),
					station.getKindDetail(),
					station.getParkingFree(),
					station.getLimitYn(),
					fastAvailable,
					slowAvailable,
					myMatch,
					externallyOpen
			));
		}
		return result;
	}
}
