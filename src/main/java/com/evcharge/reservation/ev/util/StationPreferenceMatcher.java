package com.evcharge.reservation.ev.util;

import com.evcharge.reservation.ev.domain.ChargingStation;

/**
 * 자동예약(필터) / 즐겨찾기예약(조건 불일치 시 예외)에서 쓰는
 * 주차·장소·외부개방 규칙. 규칙 자체는 기존과 동일하다.
 */
public final class StationPreferenceMatcher {

	private StationPreferenceMatcher() {
	}

	public static boolean matches(
			ChargingStation station,
			String parking,
			String place,
			boolean externalOpenOnly
	) {
		return matchesParking(station, parking)
				&& matchesPlace(station, place)
				&& matchesExternalOpen(station, externalOpenOnly);
	}

	public static boolean matchesParking(ChargingStation station, String parking) {
		if (!"FREE".equalsIgnoreCase(parking)) {
			return true;
		}
		return "Y".equalsIgnoreCase(station.getParkingFree());
	}

	public static boolean matchesPlace(ChargingStation station, String place) {
		if (place == null || "ALL".equalsIgnoreCase(place)) {
			return true;
		}
		return place.equalsIgnoreCase(nullToEmpty(station.getKind()));
	}

	public static boolean matchesExternalOpen(ChargingStation station, boolean externalOpenOnly) {
		if (!externalOpenOnly) {
			return true;
		}
		return "N".equalsIgnoreCase(station.getLimitYn());
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
