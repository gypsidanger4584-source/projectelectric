package com.evcharge.reservation.ev.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 카카오 coord2regioncode의 region_1depth_name → 공공데이터 EV API zcode.
 */
public final class RegionZcodeMapper {

	private static final Map<String, String> REGION_TO_ZCODE = new LinkedHashMap<>();

	static {
		// 카카오 정식 표기
		REGION_TO_ZCODE.put("서울특별시", "11");
		REGION_TO_ZCODE.put("부산광역시", "26");
		REGION_TO_ZCODE.put("대구광역시", "27");
		REGION_TO_ZCODE.put("인천광역시", "28");
		REGION_TO_ZCODE.put("광주광역시", "29");
		REGION_TO_ZCODE.put("대전광역시", "30");
		REGION_TO_ZCODE.put("울산광역시", "31");
		REGION_TO_ZCODE.put("세종특별자치시", "36");
		REGION_TO_ZCODE.put("경기도", "41");
		REGION_TO_ZCODE.put("강원특별자치도", "42");
		REGION_TO_ZCODE.put("충청북도", "43");
		REGION_TO_ZCODE.put("충청남도", "44");
		REGION_TO_ZCODE.put("전북특별자치도", "45");
		REGION_TO_ZCODE.put("전라남도", "46");
		REGION_TO_ZCODE.put("경상북도", "47");
		REGION_TO_ZCODE.put("경상남도", "48");
		REGION_TO_ZCODE.put("제주특별자치도", "50");

		// 약칭 / 구 표기
		REGION_TO_ZCODE.put("서울", "11");
		REGION_TO_ZCODE.put("부산", "26");
		REGION_TO_ZCODE.put("대구", "27");
		REGION_TO_ZCODE.put("인천", "28");
		REGION_TO_ZCODE.put("광주", "29");
		REGION_TO_ZCODE.put("대전", "30");
		REGION_TO_ZCODE.put("울산", "31");
		REGION_TO_ZCODE.put("세종시", "36");
		REGION_TO_ZCODE.put("세종", "36");
		REGION_TO_ZCODE.put("경기", "41");
		REGION_TO_ZCODE.put("강원도", "42");
		REGION_TO_ZCODE.put("강원", "42");
		REGION_TO_ZCODE.put("충북", "43");
		REGION_TO_ZCODE.put("충남", "44");
		REGION_TO_ZCODE.put("전라북도", "45");
		REGION_TO_ZCODE.put("전북", "45");
		REGION_TO_ZCODE.put("전남", "46");
		REGION_TO_ZCODE.put("경북", "47");
		REGION_TO_ZCODE.put("경남", "48");
		REGION_TO_ZCODE.put("제주도", "50");
		REGION_TO_ZCODE.put("제주", "50");
	}

	private RegionZcodeMapper() {
	}

	public static String toZcode(String region1DepthName) {
		if (region1DepthName == null || region1DepthName.isBlank()) {
			return null;
		}
		String trimmed = region1DepthName.trim();
		String direct = REGION_TO_ZCODE.get(trimmed);
		if (direct != null) {
			return direct;
		}
		for (Map.Entry<String, String> entry : REGION_TO_ZCODE.entrySet()) {
			if (trimmed.startsWith(entry.getKey()) || entry.getKey().startsWith(trimmed)) {
				return entry.getValue();
			}
		}
		return null;
	}
}
