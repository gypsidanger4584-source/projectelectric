package com.evcharge.reservation.ev.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.evcharge.reservation.ev.domain.User;
import com.evcharge.reservation.ev.domain.UserSetting;
import com.evcharge.reservation.ev.dto.UserSettingResponse;
import com.evcharge.reservation.ev.dto.UserSettingUpdateRequest;
import com.evcharge.reservation.ev.repository.UserRepository;
import com.evcharge.reservation.ev.repository.UserSettingRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserSettingService {

	private final UserSettingRepository userSettingRepository;
	private final UserRepository userRepository;

	@Transactional
	public UserSettingResponse getOrCreate(Long userId) {
		return toResponse(ensureSetting(userId));
	}

	@Transactional
	public UserSettingResponse update(Long userId, UserSettingUpdateRequest request) {
		UserSetting setting = ensureSetting(userId);
		setting.apply(
				request.speedPriority(),
				request.parking(),
				request.place(),
				Boolean.TRUE.equals(request.externalOpenOnly()),
				request.maxDistanceKm()
		);
		return toResponse(userSettingRepository.save(setting));
	}

	@Transactional
	public UserSetting ensureSetting(Long userId) {
		return userSettingRepository.findByUserId(userId).orElseGet(() -> {
			User user = userRepository.findById(userId)
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "유저를 찾을 수 없습니다."));
			return userSettingRepository.save(UserSetting.defaultsFor(user));
		});
	}

	private static UserSettingResponse toResponse(UserSetting setting) {
		return new UserSettingResponse(
				setting.getUser().getId(),
				setting.getSpeedPriority(),
				setting.getParking(),
				setting.getPlace(),
				setting.isExternalOpenOnly(),
				setting.getMaxDistanceKm() == null
						? UserSetting.DEFAULT_DISTANCE_KM
						: setting.getMaxDistanceKm()
		);
	}
}
