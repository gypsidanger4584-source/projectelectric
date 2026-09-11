package com.evcharge.reservation.ev.util;

/**
 * 지도/반경 계산용 순수 유틸. 동작은 기존 서비스별 private haversine과 동일하다.
 */
public final class GeoUtils {

	private static final double EARTH_RADIUS_KM = 6371.0;

	private GeoUtils() {
	}

	public static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
		double dLat = Math.toRadians(lat2 - lat1);
		double dLng = Math.toRadians(lng2 - lng1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
				+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
				* Math.sin(dLng / 2) * Math.sin(dLng / 2);
		return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
	}
}
