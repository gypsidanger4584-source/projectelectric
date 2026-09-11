package com.evcharge.reservation.ev.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.evcharge.reservation.ev.domain.ChargeSpeedBand;
import com.evcharge.reservation.ev.domain.Charger;
import com.evcharge.reservation.ev.domain.ChargerStatus;
import com.evcharge.reservation.ev.domain.ChargingStation;
import com.evcharge.reservation.ev.domain.Reservation;
import com.evcharge.reservation.ev.domain.ReservationStatus;
import com.evcharge.reservation.ev.dto.ChargingStationResponse;
import com.evcharge.reservation.ev.dto.StationDetailResponse;
import com.evcharge.reservation.ev.repository.ChargerRepository;
import com.evcharge.reservation.ev.repository.ChargingStationRepository;
import com.evcharge.reservation.ev.repository.ReservationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChargingStationService {

	private final ChargingStationRepository chargingStationRepository;
	private final ChargerRepository chargerRepository;
	private final ReservationRepository reservationRepository;

	public List<ChargingStationResponse> findAll() {
		return chargingStationRepository.findAll().stream()
				.map(ChargingStationResponse::from)
				.toList();
	}

	public ChargingStationResponse findById(Long id) {
		return chargingStationRepository.findById(id)
				.map(ChargingStationResponse::from)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "충전소를 찾을 수 없습니다."));
	}

	public StationDetailResponse getStationDetail(Long stationId) {
		return getStationDetail(stationId, null);
	}

	public StationDetailResponse getStationDetail(Long stationId, Long userId) {
		ChargingStation station = chargingStationRepository.findById(stationId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "충전소를 찾을 수 없습니다."));

		List<Charger> chargers = chargerRepository.findActiveByStationId(stationId);
		int available = 0;
		int inUse = 0;
		int waiting = 0;
		int other = 0;

		Map<Long, Reservation> myReservationByCharger = new HashMap<>();
		if (userId != null && !chargers.isEmpty()) {
			List<Long> chargerIds = chargers.stream().map(Charger::getId).toList();
			reservationRepository
					.findByUserIdAndStatusAndChargerIdIn(userId, ReservationStatus.PENDING, chargerIds)
					.forEach(r -> myReservationByCharger.put(r.getCharger().getId(), r));
		}

		List<StationDetailResponse.ChargerItem> items = new ArrayList<>(chargers.size());

		for (Charger charger : chargers) {
			ChargerStatus status = charger.getStatus();
			if (status == ChargerStatus.AVAILABLE) {
				available++;
			} else if (status == ChargerStatus.CHARGING) {
				inUse++;
			} else if (status == ChargerStatus.RESERVED) {
				waiting++;
			} else {
				other++;
			}

			ChargeSpeedBand band = ChargeSpeedBand.fromOutput(charger.getOutput());
			Reservation mine = myReservationByCharger.get(charger.getId());
			boolean myReservation = mine != null && status == ChargerStatus.RESERVED;
			items.add(new StationDetailResponse.ChargerItem(
					charger.getId(),
					charger.getChgerId(),
					charger.getChargerType() == null ? null : charger.getChargerType().name(),
					charger.getOutput(),
					status == null ? ChargerStatus.BROKEN.name() : status.name(),
					band.label(),
					myReservation ? mine.getId() : null,
					myReservation
			));
		}

		ChargeSpeedBand primary = ChargeSpeedBand.resolvePrimaryFromChargers(chargers);
		String parking = station.getParkingFree();
		String parkingLabel = "Y".equalsIgnoreCase(parking) ? "무료"
				: "N".equalsIgnoreCase(parking) ? "유료" : (parking == null || parking.isBlank() ? "-" : parking);

		return new StationDetailResponse(
				station.getId(),
				station.getStatId(),
				station.getName(),
				station.getAddress(),
				blankToDash(station.getUseTime()),
				blankToDash(station.getBusiNm()),
				blankToDash(station.getBusiCall()),
				parkingLabel,
				blankToDash(station.getNote()),
				station.getLimitYn(),
				"유료(안내가)",
				chargers.size(),
				available,
				inUse,
				waiting,
				other,
				primary.label(),
				items
		);
	}

	private static String blankToDash(String value) {
		if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
			return "-";
		}
		return value.trim();
	}
}
