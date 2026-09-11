package com.evcharge.reservation.ev.domain;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 충전기 출력(kW) 기준 속도 밴드.
 */
public enum ChargeSpeedBand {
	완속,
	중속,
	급속,
	급속플러스,
	초급속;

	/** API/프론트 표시용 라벨 (급속+ 는 enum 이름에 +를 쓸 수 없어 별도 제공). */
	public String label() {
		return this == 급속플러스 ? "급속+" : name();
	}

	public static ChargeSpeedBand fromOutput(Integer outputKw) {
		if (outputKw == null || outputKw < 30) {
			return 완속;
		}
		if (outputKw < 50) {
			return 중속;
		}
		if (outputKw < 100) {
			return 급속;
		}
		if (outputKw < 200) {
			return 급속플러스;
		}
		return 초급속;
	}

	/**
	 * 대표 밴드: 충전소에 설치된 충전기 중 최고 output 기준.
	 * 이유: 요금 안내에서 사용자가 기대하는 최고 속도 구간을 강조하는 편이
	 * “가장 많은 충전기가 속한 밴드”(다수결)보다 실용적이다.
	 */
	public static ChargeSpeedBand resolvePrimary(Collection<Integer> outputs) {
		if (outputs == null || outputs.isEmpty()) {
			return 완속;
		}
		Integer max = null;
		for (Integer output : outputs) {
			if (output == null) {
				continue;
			}
			if (max == null || output > max) {
				max = output;
			}
		}
		return fromOutput(max);
	}

	public static ChargeSpeedBand resolvePrimaryFromChargers(List<Charger> chargers) {
		if (chargers == null || chargers.isEmpty()) {
			return 완속;
		}
		return resolvePrimary(chargers.stream().map(Charger::getOutput).toList());
	}

	/** 다수결 밴드(참고용). 동률이면 더 빠른 밴드를 선택. */
	public static ChargeSpeedBand resolveByMajority(List<Charger> chargers) {
		if (chargers == null || chargers.isEmpty()) {
			return 완속;
		}
		Map<ChargeSpeedBand, Integer> counts = new EnumMap<>(ChargeSpeedBand.class);
		for (Charger charger : chargers) {
			ChargeSpeedBand band = fromOutput(charger.getOutput());
			counts.merge(band, 1, Integer::sum);
		}
		ChargeSpeedBand best = 완속;
		int bestCount = -1;
		for (ChargeSpeedBand band : ChargeSpeedBand.values()) {
			int count = counts.getOrDefault(band, 0);
			if (count > bestCount || (count == bestCount && band.ordinal() > best.ordinal())) {
				best = band;
				bestCount = count;
			}
		}
		return best;
	}
}
