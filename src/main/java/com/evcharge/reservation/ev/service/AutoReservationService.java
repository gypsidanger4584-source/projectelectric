package com.evcharge.reservation.ev.service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.evcharge.reservation.ev.domain.Charger;
import com.evcharge.reservation.ev.domain.ChargerStatus;
import com.evcharge.reservation.ev.domain.ChargingStation;
import com.evcharge.reservation.ev.domain.ReservationType;
import com.evcharge.reservation.ev.domain.User;
import com.evcharge.reservation.ev.domain.Vehicle;
import com.evcharge.reservation.ev.domain.VehicleConnectorMapping;
import com.evcharge.reservation.ev.dto.AutoReservationRequest;
import com.evcharge.reservation.ev.dto.ReservationResponse;
import com.evcharge.reservation.ev.external.KakaoMobilityService;
import com.evcharge.reservation.ev.external.KakaoMobilityService.DestinationPoint;
import com.evcharge.reservation.ev.external.KakaoMobilityService.DistanceResult;
import com.evcharge.reservation.ev.lock.LockMetrics;
import com.evcharge.reservation.ev.repository.ChargerRepository;
import com.evcharge.reservation.ev.repository.ChargingStationRepository;
import com.evcharge.reservation.ev.repository.UserRepository;
import com.evcharge.reservation.ev.repository.VehicleConnectorMappingRepository;
import com.evcharge.reservation.ev.util.GeoUtils;
import com.evcharge.reservation.ev.util.StationPreferenceMatcher;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AutoReservationService {

	private static final double DEFAULT_MAX_DISTANCE_KM = 3.0;

	private final UserRepository userRepository;
	private final ChargingStationRepository chargingStationRepository;
	private final ChargerRepository chargerRepository;
	private final VehicleConnectorMappingRepository vehicleConnectorMappingRepository;
	private final KakaoMobilityService kakaoMobilityService;
	private final ReservationService reservationService;
	private final UserSettingService userSettingService;
	private final LockMetrics lockMetrics;

	@org.springframework.beans.factory.annotation.Value("${loadtest.skip-kakao:false}")
	private boolean skipKakao;

	/**
	 * 후보 탐색(Kakao 포함)은 커넥션을 오래 잡지 않도록 NOT_SUPPORTED.
	 * 실제 점유는 reserveCharger(REQUIRES_NEW)에서만 짧게 트랜잭션을 연다.
	 * 409면 다음 후보로 폴백한다.
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public ReservationResponse autoReserve(AutoReservationRequest request) {
		Long userId = request.userId();
		double lat = request.lat();
		double lng = request.lng();

		var saved = userSettingService.getOrCreate(userId);
		Double maxDistanceKm = request.maxDistanceKm() != null
				? request.maxDistanceKm()
				: saved.maxDistanceKm();
		String parking = normalize(firstNonBlank(request.parking(), saved.parking()));
		String place = normalize(firstNonBlank(request.place(), saved.place()));
		boolean externalOpenOnly = request.externalOpenOnly() != null
				? request.externalOpenOnly()
				: saved.externalOpenOnly();

		// 요청에 speed가 있으면 하드 필터, 없으면 설정값(SLOW/FAST)을 우선순위로만 사용
		boolean hardSpeedFilter = request.speed() != null && !request.speed().isBlank();
		String speedFilter = hardSpeedFilter ? normalize(request.speed()) : "ALL";
		String speedPrefer = hardSpeedFilter ? "ALL" : normalizeSpeedPrefer(saved.speedPriority());

		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "유저를 찾을 수 없습니다."));
		Vehicle vehicle = user.getVehicle();
		if (vehicle == null || vehicle.getBrand() == null || vehicle.getModel() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "등록된 차량 정보가 없습니다.");
		}

		double radius = maxDistanceKm == null || maxDistanceKm <= 0
				? DEFAULT_MAX_DISTANCE_KM
				: Math.min(maxDistanceKm, DEFAULT_MAX_DISTANCE_KM);
		Set<String> supportedCodes = vehicleConnectorMappingRepository
				.findByBrandAndModel(vehicle.getBrand().trim(), vehicle.getModel().trim())
				.stream()
				.map(VehicleConnectorMapping::getApiChgerTypeCode)
				.collect(Collectors.toCollection(HashSet::new));
		if (supportedCodes.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "차량에 맞는 커넥터 매핑이 없습니다.");
		}

		List<Charger> candidates = findAvailableCandidates(
				lat, lng, radius, supportedCodes, speedFilter, parking, place, externalOpenOnly);
		if (candidates.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "반경 내 예약 가능한 충전기가 없습니다.");
		}

		// Kakao 다중목적지 호출 전: 좌표 유효 + 직선거리 가까운 순
		List<Charger> limitedCandidates = candidates.stream()
				.filter(c -> c.getStation() != null
						&& c.getStation().getLatitude() != null
						&& c.getStation().getLongitude() != null)
				.sorted(Comparator.comparingDouble(c ->
						GeoUtils.haversineKm(lat, lng, c.getStation().getLatitude(), c.getStation().getLongitude())))
				.limit(skipKakao ? 100 : KakaoMobilityService.MAX_DESTINATIONS)
				.toList();
		if (limitedCandidates.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "반경 내 예약 가능한 충전기가 없습니다.");
		}

		List<Charger> reachable;
		if (skipKakao) {
			// 부하 테스트용: 락/폴백 로직은 동일, 순위만 직선거리
			reachable = limitedCandidates.stream()
					.sorted(Comparator
							.comparingInt((Charger c) -> matchesPreferredSpeed(c, speedPrefer) ? 0 : 1)
							.thenComparingDouble(c -> GeoUtils.haversineKm(
									lat, lng, c.getStation().getLatitude(), c.getStation().getLongitude())))
					.toList();
		} else {
			List<DestinationPoint> destinations = limitedCandidates.stream()
					.map(c -> new DestinationPoint(
							c.getId(),
							c.getStation().getLatitude(),
							c.getStation().getLongitude()))
					.toList();

			List<DistanceResult> distances = kakaoMobilityService.getDistancesByKm(lat, lng, destinations, radius);
			Map<Long, DistanceResult> byChargerId = new HashMap<>();
			for (DistanceResult result : distances) {
				byChargerId.put(result.chargerId(), result);
			}

			int maxMeters = (int) Math.round(radius * 1000);
			reachable = limitedCandidates.stream()
					.filter(c -> {
						DistanceResult d = byChargerId.get(c.getId());
						return d != null && d.distanceMeters() <= maxMeters;
					})
					.sorted(Comparator
							.comparingInt((Charger c) -> matchesPreferredSpeed(c, speedPrefer) ? 0 : 1)
							.thenComparingInt(c -> byChargerId.get(c.getId()).durationSeconds())
							.thenComparingInt(c -> byChargerId.get(c.getId()).distanceMeters())
							.thenComparingInt(c -> {
								Integer battery = vehicle.getBatteryPercent();
								return battery == null ? Integer.MAX_VALUE : battery;
							}))
					.toList();
		}

		if (reachable.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "경로 기준 반경 내 예약 가능한 충전기가 없습니다.");
		}

		ResponseStatusException lastConflict = null;
		boolean hadConflict = false;
		for (Charger candidate : reachable) {
			try {
				ReservationResponse reserved = reservationService.reserveCharger(
						candidate.getId(),
						userId,
						ReservationType.AUTO,
						lat,
						lng
				);
				lockMetrics.recordAutoOutcome(hadConflict ? "retry_success" : "first_success");
				return reserved;
			} catch (ResponseStatusException ex) {
				// 이미 선점된 충전기면 다음 후보로 폴백
				if (ex.getStatusCode() == HttpStatus.CONFLICT) {
					hadConflict = true;
					lockMetrics.recordAutoConflictAttempt();
					lastConflict = ex;
					continue;
				}
				lockMetrics.recordAutoOutcome("error");
				throw ex;
			}
		}

		if (lastConflict != null) {
			lockMetrics.recordAutoOutcome("exhausted_conflict");
			throw lastConflict;
		}
		lockMetrics.recordAutoOutcome("not_found");
		throw new ResponseStatusException(HttpStatus.NOT_FOUND, "경로 기준 반경 내 예약 가능한 충전기가 없습니다.");
	}

	private List<Charger> findAvailableCandidates(
			double lat,
			double lng,
			double radiusKm,
			Set<String> supportedCodes,
			String speed,
			String parking,
			String place,
			boolean externalOpenOnly
	) {
		double latDelta = radiusKm / 111.0;
		double lngDelta = radiusKm / (111.0 * Math.max(0.01, Math.cos(Math.toRadians(lat))));
		List<ChargingStation> stations = chargingStationRepository.findInBoundingBox(
				lat - latDelta,
				lat + latDelta,
				lng - lngDelta,
				lng + lngDelta
		);
		List<Long> stationIds = stations.stream()
				.filter(s -> GeoUtils.haversineKm(lat, lng, s.getLatitude(), s.getLongitude()) <= radiusKm)
				.filter(s -> StationPreferenceMatcher.matches(s, parking, place, externalOpenOnly))
				.map(ChargingStation::getId)
				.toList();
		if (stationIds.isEmpty()) {
			return List.of();
		}

		return chargerRepository.findActiveByStationIdIn(stationIds).stream()
				.filter(c -> c.getStatus() == ChargerStatus.AVAILABLE)
				.filter(c -> supportedCodes.contains(c.getApiChgerTypeCode()))
				.filter(c -> matchesSpeedFilter(c, speed))
				.toList();
	}

	private static boolean matchesSpeedFilter(Charger charger, String speed) {
		if (speed == null || "ALL".equals(speed)) {
			return true;
		}
		Integer output = charger.getOutput();
		return switch (speed) {
			case "SLOW" -> isSlow(charger, output);
			case "FAST" -> output != null && output >= 50 && output < 200;
			case "ULTRA" -> output != null && output >= 200;
			default -> true;
		};
	}

	private static boolean isSlow(Charger charger, Integer output) {
		if (output != null && output >= 50) {
			return false;
		}
		if (output != null && output >= 3 && output <= 7) {
			return true;
		}
		String code = charger.getApiChgerTypeCode();
		if ("02".equals(code) || "08".equals(code)) {
			return true;
		}
		return output == null || output < 50;
	}

	private static boolean matchesPreferredSpeed(Charger charger, String prefer) {
		if (prefer == null || "ALL".equals(prefer)) {
			return true;
		}
		return matchesSpeedFilter(charger, prefer);
	}

	private static String normalizeSpeedPrefer(String value) {
		if (value == null || value.isBlank()) {
			return "FAST";
		}
		String v = value.trim().toUpperCase(Locale.ROOT);
		if ("ALL".equals(v)) {
			return "ALL";
		}
		return "SLOW".equals(v) ? "SLOW" : "FAST";
	}

	private static String firstNonBlank(String primary, String fallback) {
		if (primary != null && !primary.isBlank()) {
			return primary;
		}
		return fallback;
	}

	private static String normalize(String value) {
		if (value == null || value.isBlank()) {
			return "ALL";
		}
		return value.trim().toUpperCase(Locale.ROOT);
	}

}
