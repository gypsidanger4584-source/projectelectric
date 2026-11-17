package com.evcharge.reservation.ev.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.evcharge.reservation.ev.dto.UserSettingResponse;
import com.evcharge.reservation.ev.dto.UserSettingUpdateRequest;
import com.evcharge.reservation.ev.security.LoginUser;
import com.evcharge.reservation.ev.service.UserSettingService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class UserSettingController {

	private final UserSettingService userSettingService;

	@GetMapping("/{userId}")
	public UserSettingResponse get(
			@PathVariable Long userId,
			@AuthenticationPrincipal LoginUser loginUser
	) {
		return userSettingService.getOrCreate(ReservationApiSupport.resolveUserId(loginUser, userId));
	}

	@PutMapping("/{userId}")
	public UserSettingResponse update(
			@PathVariable Long userId,
			@RequestBody UserSettingUpdateRequest request,
			@AuthenticationPrincipal LoginUser loginUser
	) {
		Long resolvedUserId = ReservationApiSupport.resolveUserId(loginUser, userId);
		if (request == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "요청 본문이 필요합니다.");
		}
		return userSettingService.update(resolvedUserId, request);
	}
}
