package com.evcharge.reservation.ev.external;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class KakaoLocalApiClient {

	private static final String COORD2REGION_URL =
			"https://dapi.kakao.com/v2/local/geo/coord2regioncode.json";

	private final RestClient restClient;
	private final ObjectMapper objectMapper;

	@Value("${kakao.local.rest-key}")
	private String restApiKey;

	public KakaoLocalApiClient(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
		this.restClient = restClientBuilder.build();
		this.objectMapper = objectMapper;
	}

	/** 좌표 → 시/도명 (예: "서울특별시"). RegionZcodeMapper 입력용. */
	public String resolveRegion1DepthName(double lat, double lng) {
		if (restApiKey == null || restApiKey.isBlank()) {
			throw new IllegalStateException("kakao.local.rest-key is empty");
		}

		String uri = UriComponentsBuilder
				.fromUriString(COORD2REGION_URL)
				.queryParam("x", lng)
				.queryParam("y", lat)
				.toUriString();

		String body = restClient.get()
				.uri(uri)
				.header("Authorization", "KakaoAK " + restApiKey.trim())
				.header("User-Agent", "Mozilla/5.0")
				.accept(MediaType.APPLICATION_JSON)
				.retrieve()
				.body(String.class);

		JsonNode documents = objectMapper.readTree(body == null ? "{}" : body).path("documents");
		if (!documents.isArray() || documents.isEmpty()) {
			throw new IllegalArgumentException("Kakao documents empty: " + body);
		}

		for (JsonNode doc : documents) {
			String regionType = doc.path("region_type").asText();
			if ("B".equals(regionType) || "H".equals(regionType)) {
				String name = doc.path("region_1depth_name").asText();
				if (name != null && !name.isBlank()) {
					return name.trim();
				}
			}
		}

		String fallback = documents.get(0).path("region_1depth_name").asText();
		if (fallback == null || fallback.isBlank()) {
			throw new IllegalArgumentException("Kakao region_1depth_name empty: " + body);
		}
		return fallback.trim();
	}
}
