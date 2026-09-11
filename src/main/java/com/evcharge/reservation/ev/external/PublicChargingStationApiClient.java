package com.evcharge.reservation.ev.external;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.DefaultUriBuilderFactory;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PublicChargingStationApiClient {

	private final RestClient restClient;
	private final ObjectMapper objectMapper;
	private final String baseUrl;
	private final String serviceKey;
	private final int numOfRows;

	public PublicChargingStationApiClient(
			RestClient.Builder restClientBuilder,
			ObjectMapper objectMapper,
			@Value("${external-api.charging-station.base-url}") String baseUrl,
			@Value("${external-api.charging-station.service-key:}") String serviceKey,
			@Value("${external-api.charging-station.num-of-rows:1000}") int numOfRows
	) {
		this.objectMapper = objectMapper;
		this.baseUrl = baseUrl;
		this.serviceKey = normalizeServiceKey(serviceKey);
		this.numOfRows = numOfRows;
		if (!StringUtils.hasText(this.serviceKey)) {
			throw new IllegalStateException(
					"external-api.charging-station.service-key(PUBLIC_DATA_API_KEY)가 비어 있습니다.");
		}

		// 공공데이터 서비스키(/, = 포함) 이중 인코딩 방지
		DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory();
		uriBuilderFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.NONE);
		this.restClient = restClientBuilder
				.clone()
				.uriBuilderFactory(uriBuilderFactory)
				.build();
	}

	public int getNumOfRows() {
		return numOfRows;
	}

	public PageResult fetchPage(int pageNo) {
		return fetchPage(pageNo, null);
	}

	public PageResult fetchPage(int pageNo, String zcode) {
		return fetch(pageNo, zcode, null);
	}

	/**
	 * getChargerInfo의 statId 파라미터로 충전소별 조회 (수동 반경 새로고침용).
	 * API는 statId 단건 필터를 지원하므로 목록만큼 개별 호출한다.
	 */
	public List<JsonNode> fetchByStatIds(List<String> statIds) {
		if (statIds == null || statIds.isEmpty()) {
			return List.of();
		}
		List<JsonNode> allItems = new ArrayList<>();
		for (String statId : statIds) {
			if (!StringUtils.hasText(statId)) {
				continue;
			}
			PageResult page = fetch(1, null, statId.trim());
			allItems.addAll(page.items());
		}
		return allItems;
	}

	private PageResult fetch(int pageNo, String zcode, String statId) {
		String uri = buildRequestUri(pageNo, zcode, statId);

		String body = restClient.get()
				.uri(URI.create(uri))
				.accept(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN, MediaType.APPLICATION_XML)
				.retrieve()
				.body(String.class);

		if (statId == null) {
			log.info("==== [공공데이터 원본 응답 확인] (page={}) ====", pageNo);
			log.info("API Response Body: {}", body);
			log.info("===============================================");
		} else {
			log.debug("getChargerInfo statId={} itemCount approx body length={}",
					statId, body == null ? 0 : body.length());
		}

		try {
			JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
			JsonNode responseBody = extractBody(root);
			int totalCount = responseBody == null ? 0 : responseBody.path("totalCount").asInt(0);
			List<JsonNode> items = responseBody == null ? List.of() : extractItems(responseBody);
			return new PageResult(totalCount, items);
		} catch (Exception e) {
			throw new IllegalStateException(
					"Failed to parse charging station API response (page=" + pageNo + ", statId=" + statId + ")", e);
		}
	}

	/**
	 * serviceKey는 포털에서 이미 URL-인코딩된 값일 수 있으므로 추가 인코딩 없이 붙인다.
	 */
	private String buildRequestUri(int pageNo, String zcode, String statId) {
		StringBuilder uri = new StringBuilder(baseUrl);
		uri.append(baseUrl.contains("?") ? "&" : "?");
		uri.append("serviceKey=").append(serviceKey);
		uri.append("&pageNo=").append(pageNo);
		uri.append("&numOfRows=").append(numOfRows);
		uri.append("&dataType=JSON");
		if (StringUtils.hasText(zcode)) {
			uri.append("&zcode=").append(zcode.trim());
		}
		if (StringUtils.hasText(statId)) {
			uri.append("&statId=").append(statId.trim());
		}
		return uri.toString();
	}

	private static String normalizeServiceKey(String key) {
		if (key == null) {
			return "";
		}
		String trimmed = key.trim();
		if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
				|| (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
			trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
		}
		return trimmed;
	}

	private JsonNode extractBody(JsonNode root) {
		if (root == null) {
			return null;
		}
		if (root.has("totalCount") || root.has("items")) {
			return root;
		}
		JsonNode response = root.path("response");
		if (!response.isMissingNode()) {
			JsonNode body = response.path("body");
			if (!body.isMissingNode()) {
				return body;
			}
		}
		JsonNode body = root.path("body");
		return body.isMissingNode() ? root : body;
	}

	private List<JsonNode> extractItems(JsonNode body) {
		JsonNode itemsNode = body.path("items");
		if (itemsNode.isMissingNode() || itemsNode.isNull()) {
			return Collections.emptyList();
		}
		JsonNode itemNode = itemsNode.path("item");
		if (itemNode.isMissingNode() || itemNode.isNull()) {
			if (itemsNode.isArray()) {
				List<JsonNode> list = new ArrayList<>();
				itemsNode.forEach(list::add);
				return list;
			}
			return Collections.emptyList();
		}
		if (itemNode.isArray()) {
			List<JsonNode> list = new ArrayList<>();
			itemNode.forEach(list::add);
			return list;
		}
		return List.of(itemNode);
	}

	public record PageResult(int totalCount, List<JsonNode> items) {
	}
}
