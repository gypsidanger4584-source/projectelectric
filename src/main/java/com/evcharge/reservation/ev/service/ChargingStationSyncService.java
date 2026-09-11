package com.evcharge.reservation.ev.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PostConstruct;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.evcharge.reservation.ev.domain.Charger;
import com.evcharge.reservation.ev.domain.ChargerStatus;
import com.evcharge.reservation.ev.domain.ChargerType;
import com.evcharge.reservation.ev.domain.ChargerTypeMapper;
import com.evcharge.reservation.ev.domain.ChargingStation;
import com.evcharge.reservation.ev.exception.TooManyRequestsException;
import com.evcharge.reservation.ev.external.PublicChargingStationApiClient;
import com.evcharge.reservation.ev.repository.ChargerRepository;
import com.evcharge.reservation.ev.repository.ChargingStationRepository;
import com.evcharge.reservation.ev.util.GeoUtils;
import tools.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ChargingStationSyncService {

	private static final DateTimeFormatter API_DT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
	private static final long MANUAL_COOLDOWN_MS = 30_000L;
	private static final double DEFAULT_REFRESH_RADIUS_KM = 3.0;

	private final PublicChargingStationApiClient apiClient;
	private final ChargingStationRepository chargingStationRepository;
	private final ChargerRepository chargerRepository;
	private final TransactionTemplate transactionTemplate;

	/** 위치 기반 수동 새로고침 쿨다운 (lat/lng 소수점 3자리 ≈ 100m 격자) */
	private final ConcurrentHashMap<String, Long> lastManualSyncByLocation = new ConcurrentHashMap<>();

	public ChargingStationSyncService(
			PublicChargingStationApiClient apiClient,
			ChargingStationRepository chargingStationRepository,
			ChargerRepository chargerRepository,
			PlatformTransactionManager transactionManager
	) {
		this.apiClient = apiClient;
		this.chargingStationRepository = chargingStationRepository;
		this.chargerRepository = chargerRepository;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	@PostConstruct
	public void onStartup() {
		long count = chargingStationRepository.count();
		if (count == 0) {
			log.info("DB에 충전소 데이터가 없어 최초 동기화를 시작합니다.");
			try {
				syncAll();
			} catch (Exception e) {
				log.error("최초 충전소 동기화 실패 (서버는 정상 기동합니다)", e);
			}
		} else {
			log.info("DB에 이미 충전소 {}건이 있어 시작 시 자동 동기화를 건너뜁니다. 필요 시 수동 새로고침을 사용하세요.", count);
		}
	}

	/**
	 * 반경 내 충전소만 공공 API(statId)로 상태 갱신. 수동 새로고침용.
	 */
	public synchronized int syncByRadius(double lat, double lng, Double radiusKm) {
		double radius = radiusKm == null || radiusKm <= 0 ? DEFAULT_REFRESH_RADIUS_KM : radiusKm;
		String cooldownKey = locationCooldownKey(lat, lng, radius);
		long now = System.currentTimeMillis();
		Long previous = lastManualSyncByLocation.get(cooldownKey);
		if (previous != null && now - previous < MANUAL_COOLDOWN_MS) {
			throw new TooManyRequestsException("해당 위치 수동 새로고침은 30초 간격으로만 가능합니다.");
		}
		lastManualSyncByLocation.put(cooldownKey, now);

		List<String> statIds = findStatIdsWithinRadius(lat, lng, radius);
		if (statIds.isEmpty()) {
			log.info("반경 새로고침: 대상 충전소 없음 lat={}, lng={}, radiusKm={}", lat, lng, radius);
			return 0;
		}

		log.info("반경 새로고침 시작: stations={}, lat={}, lng={}, radiusKm={}",
				statIds.size(), lat, lng, radius);
		List<JsonNode> items = apiClient.fetchByStatIds(statIds);
		LocalDateTime syncedAt = LocalDateTime.now();

		Map<String, List<JsonNode>> byStatId = new LinkedHashMap<>();
		for (JsonNode item : items) {
			String statId = text(item, "statId");
			if (statId == null || statId.isBlank()) {
				continue;
			}
			byStatId.computeIfAbsent(statId, key -> new ArrayList<>()).add(item);
		}

		for (Map.Entry<String, List<JsonNode>> entry : byStatId.entrySet()) {
			transactionTemplate.executeWithoutResult(status ->
					upsertStationWithChargers(entry.getKey(), entry.getValue(), syncedAt));
		}

		log.info("반경 새로고침 완료: requested={}, updatedStations={}, apiItems={}",
				statIds.size(), byStatId.size(), items.size());
		return byStatId.size();
	}

	/** 시/도 전체 강제 동기화(관리/디버그용). 일반 새로고침 경로에서는 사용하지 않음. */
	public synchronized void forceSyncNow(String zcode) {
		try {
			if (zcode == null || zcode.isBlank()) {
				throw new IllegalArgumentException("zcode는 필수입니다.");
			}
			syncByZcode(zcode.trim());
		} catch (Exception e) {
			log.error("Refresh Error (forceSyncNow): zcode={}", zcode, e);
			e.printStackTrace();
			throw e;
		}
	}

	public void syncAll() {
		syncPages(null);
	}

	public void syncByZcode(String zcode) {
		try {
			if (zcode == null || zcode.isBlank()) {
				throw new IllegalArgumentException("zcode는 필수입니다.");
			}
			syncPages(zcode.trim());
		} catch (Exception e) {
			log.error("Refresh Error (syncByZcode): zcode={}", zcode, e);
			e.printStackTrace();
			throw e;
		}
	}

	private List<String> findStatIdsWithinRadius(double lat, double lng, double radiusKm) {
		double latDelta = radiusKm / 111.0;
		double lngDelta = radiusKm / (111.0 * Math.max(0.01, Math.cos(Math.toRadians(lat))));

		List<ChargingStation> candidates = chargingStationRepository.findInBoundingBox(
				lat - latDelta,
				lat + latDelta,
				lng - lngDelta,
				lng + lngDelta
		);

		return candidates.stream()
				.filter(s -> s.getStatId() != null && !s.getStatId().isBlank())
				.filter(s -> GeoUtils.haversineKm(lat, lng, s.getLatitude(), s.getLongitude()) <= radiusKm)
				.map(ChargingStation::getStatId)
				.distinct()
				.toList();
	}

	private static String locationCooldownKey(double lat, double lng, double radiusKm) {
		return String.format("%.3f:%.3f:%.1f", lat, lng, radiusKm);
	}

	private void syncPages(String zcode) {
		LocalDateTime syncedAt = LocalDateTime.now();
		int pageNo = 1;
		int totalCount = Integer.MAX_VALUE;
		int processed = 0;
		int numOfRows = apiClient.getNumOfRows();
		String scope = zcode == null ? "ALL" : zcode;

		while ((pageNo - 1L) * numOfRows < totalCount) {
			PublicChargingStationApiClient.PageResult page = apiClient.fetchPage(pageNo, zcode);
			totalCount = page.totalCount();
			List<JsonNode> items = page.items();
			if (items.isEmpty()) {
				break;
			}

			Map<String, List<JsonNode>> byStatId = new LinkedHashMap<>();
			for (JsonNode item : items) {
				String statId = text(item, "statId");
				if (statId == null || statId.isBlank()) {
					continue;
				}
				byStatId.computeIfAbsent(statId, key -> new ArrayList<>()).add(item);
			}

			for (Map.Entry<String, List<JsonNode>> entry : byStatId.entrySet()) {
				transactionTemplate.executeWithoutResult(status ->
						upsertStationWithChargers(entry.getKey(), entry.getValue(), syncedAt));
			}

			processed += items.size();
			log.info("Synced page {} scope={} ({} rows, processed={}, totalCount={})",
					pageNo, scope, items.size(), processed, totalCount);
			pageNo++;
			if (pageNo > 5000) {
				break;
			}
		}
		log.info("Charging station sync completed. scope={}, processed={}", scope, processed);
	}

	private void upsertStationWithChargers(String statId, List<JsonNode> items, LocalDateTime syncedAt) {
		JsonNode first = items.get(0);
		Double lat = asDouble(first, "lat");
		Double lng = asDouble(first, "lng");
		if (lat == null || lng == null) {
			return;
		}

		String name = textOrDefault(first, "statNm", statId);
		String address = textOrDefault(first, "addr", "-");

		ChargingStation station = chargingStationRepository.findByStatId(statId)
				.orElseGet(() -> ChargingStation.builder()
						.statId(statId)
						.name(name)
						.address(address)
						.latitude(lat)
						.longitude(lng)
						.build());

		station.applyApiSync(
				name,
				address,
				text(first, "addrDetail"),
				text(first, "location"),
				text(first, "useTime"),
				text(first, "busiId"),
				text(first, "bnm"),
				text(first, "busiNm"),
				text(first, "busiCall"),
				text(first, "zcode"),
				text(first, "zscode"),
				text(first, "kind"),
				text(first, "kindDetail"),
				text(first, "parkingFree"),
				text(first, "note"),
				textOrDefault(first, "limitYn", "N"),
				text(first, "limitDetail"),
				asInteger(first, "year"),
				text(first, "floorNum"),
				text(first, "floorType"),
				lat,
				lng,
				syncedAt
		);
		station = chargingStationRepository.save(station);

		for (JsonNode item : items) {
			String chgerId = text(item, "chgerId");
			if (chgerId == null || chgerId.isBlank()) {
				continue;
			}
			String apiCode = normalizeChgerType(text(item, "chgerType"));
			Integer parsedStatCode = asInteger(item, "stat");
			final int resolvedStatCode = parsedStatCode == null ? 0 : parsedStatCode;
			ChargerType chargerType = ChargerTypeMapper.fromApiCode(apiCode);
			ChargerStatus status = ChargerTypeMapper.fromStatCode(resolvedStatCode);

			final ChargingStation savedStation = station;
			Charger charger = chargerRepository.findByStationIdAndChgerId(savedStation.getId(), chgerId)
					.orElseGet(() -> Charger.builder()
							.station(savedStation)
							.chgerId(chgerId)
							.chargerType(chargerType)
							.apiChgerTypeCode(apiCode)
							.status(status)
							.statCode(resolvedStatCode)
							.delYn("N")
							.build());

			charger.applyApiSync(
					chargerType,
					apiCode,
					asInteger(item, "output"),
					text(item, "method"),
					status,
					resolvedStatCode,
					asDateTime(item, "statUpdDt"),
					asDateTime(item, "lastTsdt"),
					asDateTime(item, "lastTedt"),
					asDateTime(item, "nowTsdt"),
					textOrDefault(item, "delYn", "N"),
					syncedAt
			);
			chargerRepository.save(charger);
		}
	}

	private static String normalizeChgerType(String code) {
		if (code == null || code.isBlank()) {
			return "04";
		}
		String trimmed = code.trim();
		return trimmed.length() == 1 ? "0" + trimmed : trimmed;
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (value == null || value.isNull()) {
			return null;
		}
		String text = value.asText();
		if (text == null || text.isBlank() || "null".equalsIgnoreCase(text)) {
			return null;
		}
		return text.trim();
	}

	private static String textOrDefault(JsonNode node, String field, String defaultValue) {
		String value = text(node, field);
		return value == null ? defaultValue : value;
	}

	private static Double asDouble(JsonNode node, String field) {
		String value = text(node, field);
		if (value == null) {
			return null;
		}
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static Integer asInteger(JsonNode node, String field) {
		String value = text(node, field);
		if (value == null) {
			return null;
		}
		try {
			return (int) Double.parseDouble(value);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static LocalDateTime asDateTime(JsonNode node, String field) {
		String value = text(node, field);
		if (value == null) {
			return null;
		}
		try {
			return LocalDateTime.parse(value, API_DT);
		} catch (DateTimeParseException e) {
			return null;
		}
	}
}
