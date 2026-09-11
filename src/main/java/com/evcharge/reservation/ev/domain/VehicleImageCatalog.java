package com.evcharge.reservation.ev.domain;

import java.util.Collections;
import java.util.Map;

/**
 * 가입 차종(brand + model) → 메인 페이지 차량 이미지 경로.
 * 현재는 모든 차종을 아이오닉5 이미지로 통일한다.
 */
public final class VehicleImageCatalog {

	public static final String DEFAULT_IMAGE = "/images/ioniq5n.webp";

	private VehicleImageCatalog() {
	}

	public static String resolve(String brand, String model) {
		return DEFAULT_IMAGE;
	}

	public static Map<String, String> mappings() {
		return Collections.emptyMap();
	}
}
