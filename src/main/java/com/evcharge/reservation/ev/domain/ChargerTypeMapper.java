package com.evcharge.reservation.ev.domain;

public final class ChargerTypeMapper {

	private ChargerTypeMapper() {
	}

	public static ChargerType fromApiCode(String apiChgerTypeCode) {
		if ("02".equals(apiChgerTypeCode) || "08".equals(apiChgerTypeCode)) {
			return ChargerType.AC_SLOW;
		}
		return ChargerType.DC_FAST;
	}

	public static ChargerStatus fromStatCode(Integer statCode) {
		if (statCode == null) {
			return ChargerStatus.BROKEN;
		}
		return switch (statCode) {
			case 2 -> ChargerStatus.AVAILABLE;
			case 3 -> ChargerStatus.CHARGING;
			default -> ChargerStatus.BROKEN;
		};
	}

	public static String toApiCode(ChargerType chargerType) {
		return chargerType == ChargerType.AC_SLOW ? "02" : "04";
	}

	public static int toStatCode(ChargerStatus status) {
		return switch (status) {
			case AVAILABLE -> 2;
			case CHARGING -> 3;
			case RESERVED -> 1;
			case BROKEN -> 5;
		};
	}
}
