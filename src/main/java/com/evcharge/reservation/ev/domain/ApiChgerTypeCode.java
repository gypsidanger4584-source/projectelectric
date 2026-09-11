package com.evcharge.reservation.ev.domain;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ApiChgerTypeCode {
	CODE_01("01", "DC차데모"),
	CODE_02("02", "AC완속"),
	CODE_03("03", "DC차데모+AC3상"),
	CODE_04("04", "DC콤보"),
	CODE_05("05", "DC차데모+DC콤보"),
	CODE_06("06", "DC차데모+AC3상+DC콤보"),
	CODE_07("07", "AC3상"),
	CODE_08("08", "DC콤보(완속)"),
	CODE_09("09", "NACS"),
	CODE_10("10", "DC콤보+NACS"),
	CODE_11("11", "DC콤보2(버스전용)");

	private final String code;
	private final String description;

	private static final Map<String, ApiChgerTypeCode> BY_CODE = Arrays.stream(values())
			.collect(Collectors.toUnmodifiableMap(ApiChgerTypeCode::getCode, Function.identity()));

	public static ApiChgerTypeCode fromCode(String code) {
		return BY_CODE.get(code);
	}
}
