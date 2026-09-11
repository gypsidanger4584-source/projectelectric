package com.evcharge.reservation.ev.dto;

import java.util.List;

public record StationDetailResponse(
		Long stationId,
		String statId,
		String name,
		String address,
		String useTime,
		String busiNm,
		String busiCall,
		String parkingFree,
		String note,
		String limitYn,
		String chargingFee,
		int totalChargerCount,
		int availableCount,
		int inUseCount,
		int waitingCount,
		int otherCount,
		String primaryBand,
		List<ChargerItem> chargers
) {
	public record ChargerItem(
			Long id,
			String chgerId,
			String chargerType,
			Integer output,
			String status,
			String speedBand,
			Long reservationId,
			boolean myReservation
	) {
	}
}
