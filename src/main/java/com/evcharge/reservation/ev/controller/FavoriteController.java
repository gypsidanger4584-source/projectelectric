package com.evcharge.reservation.ev.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.evcharge.reservation.ev.dto.FavoriteCreateRequest;
import com.evcharge.reservation.ev.dto.FavoriteResponse;
import com.evcharge.reservation.ev.dto.ReservationResponse;
import com.evcharge.reservation.ev.security.LoginUser;
import com.evcharge.reservation.ev.service.FavoriteService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

	private final FavoriteService favoriteService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public FavoriteResponse add(
			@RequestBody FavoriteCreateRequest request,
			@AuthenticationPrincipal LoginUser loginUser
	) {
		Long userId = ReservationApiSupport.resolveUserId(loginUser, request.userId());
		if (request.stationId() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stationId는 필수입니다.");
		}
		return favoriteService.add(userId, request.stationId());
	}

	@GetMapping("/{userId}")
	public List<FavoriteResponse> list(
			@PathVariable Long userId,
			@AuthenticationPrincipal LoginUser loginUser
	) {
		return favoriteService.listByUser(ReservationApiSupport.resolveUserId(loginUser, userId));
	}

	@PostMapping("/{stationId}/reserve")
	@ResponseStatus(HttpStatus.CREATED)
	public ReservationResponse reserve(
			@PathVariable Long stationId,
			@RequestParam(required = false) Long userId,
			@AuthenticationPrincipal LoginUser loginUser
	) {
		return favoriteService.reserveFromFavorite(stationId, ReservationApiSupport.resolveUserId(loginUser, userId));
	}

	@DeleteMapping("/{stationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void remove(
			@PathVariable Long stationId,
			@RequestParam(required = false) Long userId,
			@AuthenticationPrincipal LoginUser loginUser
	) {
		favoriteService.remove(ReservationApiSupport.resolveUserId(loginUser, userId), stationId);
	}
}
