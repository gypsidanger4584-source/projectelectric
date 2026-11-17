package com.evcharge.reservation.ev.controller;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.evcharge.reservation.ev.lock.LockStrategyHolder;

import lombok.RequiredArgsConstructor;

/**
 * 락 전략 전환 (데모·부하 테스트 전용).
 * 역할(role) 체계가 없어 로그인한 누구나 전역 설정을 바꿀 수 있으므로, 기본은 빈 자체를 만들지 않는다.
 * 로컬 테스트 시에만 {@code APP_ADMIN_LOCK_STRATEGY_TOGGLE=true} (app.admin.lock-strategy-toggle) 로 켠다.
 */
@RestController
@ConditionalOnProperty(prefix = "app.admin", name = "lock-strategy-toggle", havingValue = "true")
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
