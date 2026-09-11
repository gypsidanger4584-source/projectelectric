package com.evcharge.reservation.ev.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
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
import com.evcharge.reservation.ev.service.FavoriteService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

	private final FavoriteService favoriteService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public FavoriteResponse add(@RequestBody FavoriteCreateRequest request) {
		if (request.userId() == null || request.stationId() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId와 stationId는 필수입니다.");
		}
		return favoriteService.add(request.userId(), request.stationId());
	}

	@GetMapping("/{userId}")
	public List<FavoriteResponse> list(@PathVariable Long userId) {
		return favoriteService.listByUser(userId);
	}

	@PostMapping("/{stationId}/reserve")
	@ResponseStatus(HttpStatus.CREATED)
	public ReservationResponse reserve(
			@PathVariable Long stationId,
			@RequestParam Long userId
	) {
		return favoriteService.reserveFromFavorite(stationId, userId);
	}

	@org.springframework.web.bind.annotation.DeleteMapping("/{stationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void remove(
			@PathVariable Long stationId,
			@RequestParam Long userId
	) {
		favoriteService.remove(userId, stationId);
	}
}
