package com.evcharge.reservation.ev.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.evcharge.reservation.ev.lock.LockStrategyHolder;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/lock-strategy")
@RequiredArgsConstructor
public class LockStrategyController {

	private final LockStrategyHolder holder;

	@GetMapping
	public Map<String, String> get() {
		return Map.of("strategy", holder.get());
	}

	@PostMapping
	public void set(@RequestBody Map<String, String> body) {
		holder.set(body == null ? null : body.get("strategy"));
	}
}
