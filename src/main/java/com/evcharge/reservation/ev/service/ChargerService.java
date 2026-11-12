package com.evcharge.reservation.ev.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
		if (!chargingStationRepository.existsById(stationId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "충전소를 찾을 수 없습니다.");
		}
		return chargerRepository.findByStationId(stationId).stream()
				.map(ChargerResponse::from)
				.toList();
	}
}
