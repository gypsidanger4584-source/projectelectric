package com.evcharge.reservation.ev.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.evcharge.reservation.ev.domain.Charger;
import com.evcharge.reservation.ev.domain.ChargingStation;
import com.evcharge.reservation.ev.domain.ChargerTypeMapper;
import com.evcharge.reservation.ev.dto.ChargerCreateRequest;
import com.evcharge.reservation.ev.dto.ChargerResponse;
import com.evcharge.reservation.ev.repository.ChargerRepository;
import com.evcharge.reservation.ev.repository.ChargingStationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChargerService {

	private final ChargerRepository chargerRepository;
	private final ChargingStationRepository chargingStationRepository;

	public List<ChargerResponse> findByStationId(Long stationId) {
		findStation(stationId);
		return chargerRepository.findByStationId(stationId).stream()
				.map(ChargerResponse::from)
				.toList();
	}

	@Transactional
	public ChargerResponse create(Long stationId, ChargerCreateRequest request) {
		ChargingStation station = findStation(stationId);
		String apiCode = ChargerTypeMapper.toApiCode(request.chargerType());
		int statCode = ChargerTypeMapper.toStatCode(request.status());
		Charger charger = Charger.builder()
				.station(station)
				.chgerId("L" + UUID.randomUUID().toString().replace("-", "").substring(0, 8))
				.chargerType(request.chargerType())
				.apiChgerTypeCode(apiCode)
				.status(request.status())
				.statCode(statCode)
				.delYn("N")
				.lastSyncedAt(LocalDateTime.now())
				.build();
		return ChargerResponse.from(chargerRepository.save(charger));
	}

	private ChargingStation findStation(Long stationId) {
		return chargingStationRepository.findById(stationId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "충전소를 찾을 수 없습니다."));
	}
}
