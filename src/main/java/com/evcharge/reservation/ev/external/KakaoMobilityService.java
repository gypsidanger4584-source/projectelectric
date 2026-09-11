package com.evcharge.reservation.ev.external;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class KakaoMobilityService {

	/** 길찾기 API radius 상한(m). destinations/directions 기준 10000. */
	public static final int MAX_RADIUS_METERS = 10_000;
	public static final int DEFAULT_RADIUS_METERS = 3_000;
	public static final int MAX_DESTINATIONS = 30;

	private final RestTemplate restTemplate;
	private final ObjectMapper objectMapper;
	private final String restKey;
	private final String baseUrl;

	public KakaoMobilityService(
			RestTemplate restTemplate,
			ObjectMapper objectMapper,
			@Value("${kakao.mobility.rest-key:}") String restKey,
			@Value("${kakao.mobility.base-url}") String baseUrl
	) {
		this.restTemplate = restTemplate;
		this.objectMapper = objectMapper;
		this.restKey = restKey == null ? "" : restKey.trim();
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
	}

	/**
	 * @param radiusMeters 검색 반경(m). null/이하이면 DEFAULT, 상한 MAX_RADIUS_METERS.
	 */
	public List<DistanceResult> getDistances(
			double originLat,
			double originLng,
			List<DestinationPoint> destinations,
			Integer radiusMeters
	) {
		if (!StringUtils.hasText(restKey)) {
			throw new ResponseStatusException(
					HttpStatus.BAD_GATEWAY,
					"카카오 모빌리티 API 키가 설정되지 않았습니다. (KAKAO_REST_API_KEY)"
			);
		}
		if (destinations == null || destinations.isEmpty()) {
			return List.of();
		}
		if (destinations.size() > MAX_DESTINATIONS) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"길찾기 목적지는 최대 " + MAX_DESTINATIONS + "개까지 가능합니다."
			);
		}

		int radius = resolveRadiusMeters(radiusMeters);

		Map<String, Object> body = new HashMap<>();
		body.put("origin", Map.of("x", originLng, "y", originLat));

		List<Map<String, Object>> destPayload = new ArrayList<>();
		for (DestinationPoint point : destinations) {
			destPayload.add(Map.of(
					"x", point.lng(),
					"y", point.lat(),
					"key", String.valueOf(point.chargerId())
			));
		}
		body.put("destinations", destPayload);
		body.put("radius", radius);
		body.put("priority", "TIME");

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("Authorization", "KakaoAK " + restKey);

		String url = baseUrl + "/v1/destinations/directions";
		try {
			ResponseEntity<String> response = restTemplate.postForEntity(
					url,
					new HttpEntity<>(body, headers),
					String.class
			);
			return parseDistanceResults(response.getBody());
		} catch (RestClientException e) {
			log.error("Kakao Mobility request failed: {}", e.getMessage());
			throw new ResponseStatusException(
					HttpStatus.BAD_GATEWAY,
					"경로 조회에 실패했습니다. 잠시 후 다시 시도해주세요.",
					e
			);
		}
	}

	/** maxDistanceKm → meters 변환 후 상한 적용. */
	public List<DistanceResult> getDistancesByKm(
			double originLat,
			double originLng,
			List<DestinationPoint> destinations,
			Double maxDistanceKm
	) {
		return getDistances(originLat, originLng, destinations, toRadiusMeters(maxDistanceKm));
	}

	public Integer getDurationSeconds(
			double originLat,
			double originLng,
			double destLat,
			double destLng,
			Long chargerId,
			Integer radiusMeters
	) {
		List<DistanceResult> results = getDistances(
				originLat,
				originLng,
				List.of(new DestinationPoint(chargerId, destLat, destLng)),
				radiusMeters
		);
		if (results.isEmpty()) {
			return null;
		}
		return results.get(0).durationSeconds();
	}

	public Integer getDurationSeconds(
			double originLat,
			double originLng,
			double destLat,
			double destLng,
			Long chargerId
	) {
		return getDurationSeconds(originLat, originLng, destLat, destLng, chargerId, MAX_RADIUS_METERS);
	}

	static int toRadiusMeters(Double maxDistanceKm) {
		if (maxDistanceKm == null || maxDistanceKm <= 0) {
			return DEFAULT_RADIUS_METERS;
		}
		return resolveRadiusMeters((int) Math.round(maxDistanceKm * 1000.0));
	}

	static int resolveRadiusMeters(Integer radiusMeters) {
		if (radiusMeters == null || radiusMeters <= 0) {
			return DEFAULT_RADIUS_METERS;
		}
		return Math.min(radiusMeters, MAX_RADIUS_METERS);
	}

	/**
	 * 응답의 routes[](또는 destinations[]) 항목 key로 chargerId를 매칭한다.
	 * key는 요청 시 destinations[].key에 넣은 chargerId 문자열과 동일하다.
	 */
	private List<DistanceResult> parseDistanceResults(String body) {
		try {
			JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
			JsonNode items = root.path("routes");
			if (!items.isArray() || items.isEmpty()) {
				items = root.path("destinations");
			}
			if (!items.isArray()) {
				log.warn("Kakao mobility response without routes/destinations: {}", body);
				return List.of();
			}

			List<DistanceResult> results = new ArrayList<>();
			for (JsonNode item : items) {
				int resultCode = item.path("result_code").asInt(0);
				if (resultCode != 0) {
					continue;
				}
				String key = textOrNull(item.path("key"));
				if (key == null || key.isBlank()) {
					continue;
				}
				JsonNode summary = item.path("summary");
				int duration = summary.path("duration").asInt(item.path("duration").asInt(0));
				int distance = summary.path("distance").asInt(item.path("distance").asInt(0));
				try {
					results.add(new DistanceResult(Long.parseLong(key.trim()), duration, distance));
				} catch (NumberFormatException e) {
					log.warn("Kakao mobility key is not a chargerId long: {}", key);
				}
			}
			return results;
		} catch (ResponseStatusException e) {
			throw e;
		} catch (Exception e) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Kakao Mobility 응답 파싱 실패", e);
		}
	}

	private static String textOrNull(JsonNode node) {
		if (node == null || node.isMissingNode() || node.isNull()) {
			return null;
		}
		String text = node.asText();
		return (text == null || text.isBlank() || "null".equals(text)) ? null : text;
	}

	public record DestinationPoint(Long chargerId, double lat, double lng) {
	}

	public record DistanceResult(Long chargerId, int durationSeconds, int distanceMeters) {
	}
}
