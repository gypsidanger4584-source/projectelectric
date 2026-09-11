package com.evcharge.reservation.ev.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * signup.html EV_DATA / vehicle_connector_mapping과 동일한 25개 차종별
 * 배터리 용량(kWh) + 전비(efficiency, km/kWh) 범위 매핑.
 * key 형식: "{brand} {model}" (예: "현대 아이오닉5")
 */
public final class EvBatteryCatalog {

	private static final double DEFAULT_CAPACITY_KWH = 60.0;
	private static final double DEFAULT_EFFICIENCY_MIN = 4.0;
	private static final double DEFAULT_EFFICIENCY_MAX = 6.0;

	public record VehicleSpec(double capacityKwh, double efficiencyMin, double efficiencyMax) {
	}

	private static final Map<String, VehicleSpec> SPEC_BY_KEY;

	static {
		Map<String, VehicleSpec> map = new LinkedHashMap<>();

		// 소형/중형 세단·고효율: 5.5~7
		map.put("테슬라 Model3", spec(57.5, 5.5, 7.0));
		map.put("테슬라 ModelY", spec(75.0, 5.5, 7.0));
		map.put("테슬라 ModelS", spec(100.0, 5.5, 7.0));
		map.put("테슬라 ModelX", spec(100.0, 5.5, 7.0));
		map.put("현대 아이오닉6", spec(77.4, 5.5, 7.0));
		map.put("쉐보레 볼트EV", spec(65.0, 5.5, 7.0));

		// SUV: 4.5~5.5
		map.put("현대 아이오닉5", spec(77.4, 4.5, 5.5));
		map.put("현대 아이오닉9", spec(100.0, 4.5, 5.5));
		map.put("현대 코나EV", spec(64.8, 4.5, 5.5));
		map.put("현대 넥쏘", spec(60.0, 4.5, 5.5));
		map.put("기아 EV6", spec(77.4, 4.5, 5.5));
		map.put("기아 EV9", spec(99.8, 4.5, 5.5));
		map.put("기아 니로EV", spec(64.8, 4.5, 5.5));
		map.put("기아 EV3", spec(58.3, 4.5, 5.5));
		map.put("기아 EV4", spec(58.3, 4.5, 5.5));

		// 고성능/대형: 3.0~4.2
		map.put("포르쉐 타이칸", spec(93.4, 3.0, 4.2));
		map.put("포르쉐 타이칸크로스투리스모", spec(93.4, 3.0, 4.2));
		map.put("포르쉐 마칸일렉트릭", spec(95.0, 3.0, 4.2));
		map.put("포르쉐 타이칸4S", spec(93.4, 3.0, 4.2));
		map.put("포르쉐 타이칸터보", spec(93.4, 3.0, 4.2));

		// 초대형/픽업·대형 SUV: 2.5~3.8
		map.put("테슬라 Cybertruck", spec(123.0, 2.5, 3.8));
		map.put("쉐보레 볼트EUV", spec(65.0, 2.5, 3.8));
		map.put("쉐보레 이쿼녹스EV", spec(85.0, 2.5, 3.8));
		map.put("쉐보레 블레이저EV", spec(85.0, 2.5, 3.8));
		map.put("쉐보레 실버라도EV", spec(168.0, 2.5, 3.8));

		SPEC_BY_KEY = Collections.unmodifiableMap(map);
	}

	private EvBatteryCatalog() {
	}

	private static VehicleSpec spec(double capacityKwh, double efficiencyMin, double efficiencyMax) {
		return new VehicleSpec(capacityKwh, efficiencyMin, efficiencyMax);
	}

	public static Map<String, VehicleSpec> specs() {
		return SPEC_BY_KEY;
	}

	public static Map<String, Double> capacities() {
		Map<String, Double> map = new LinkedHashMap<>();
		SPEC_BY_KEY.forEach((k, v) -> map.put(k, v.capacityKwh()));
		return Collections.unmodifiableMap(map);
	}

	public static VehicleSpec resolveSpec(String brand, String model) {
		if (brand == null || model == null) {
			return new VehicleSpec(DEFAULT_CAPACITY_KWH, DEFAULT_EFFICIENCY_MIN, DEFAULT_EFFICIENCY_MAX);
		}
		return SPEC_BY_KEY.getOrDefault(
				brand.trim() + " " + model.trim(),
				new VehicleSpec(DEFAULT_CAPACITY_KWH, DEFAULT_EFFICIENCY_MIN, DEFAULT_EFFICIENCY_MAX)
		);
	}

	public static double resolveCapacityKwh(String brand, String model) {
		return resolveSpec(brand, model).capacityKwh();
	}

	/** 차종별 efficiencyMin~Max 구간에서 균등 랜덤 (소수 첫째 자리). */
	public static double resolveRandomEfficiency(String brand, String model) {
		VehicleSpec spec = resolveSpec(brand, model);
		double min = spec.efficiencyMin();
		double max = spec.efficiencyMax();
		if (max < min) {
			double t = min;
			min = max;
			max = t;
		}
		double raw = min + ThreadLocalRandom.current().nextDouble() * (max - min);
		return Math.round(raw * 10.0) / 10.0;
	}
}
