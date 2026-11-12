package com.evcharge.reservation.ev.security;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthRateLimitService {

	static final int MAX_LOGIN_FAILS_PER_USER = 5;
	static final int MAX_LOGIN_FAILS_PER_IP = 30;
	static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);
	static final Duration LOGIN_LOCK = Duration.ofMinutes(15);

	static final int MAX_SIGNUPS_PER_IP = 5;
	static final Duration SIGNUP_WINDOW = Duration.ofHours(1);

	/** 공공 API 수동 새로고침: 좌표를 바꿔 위치별 쿨다운을 우회하지 못하도록 사용자 단위로도 제한 */
	static final int MAX_REFRESHES_PER_USER = 5;
	static final Duration REFRESH_WINDOW = Duration.ofMinutes(1);

	private final RedissonClient redissonClient;

	public boolean isLoginBlocked(String username, String ip) {
		return exists(userLockKey(username)) || exists(ipLockKey(ip));
	}

	public void onLoginFailure(String username, String ip) {
		if (StringUtils.hasText(username)) {
			long fails = increment(userFailKey(username), LOGIN_WINDOW);
			if (fails >= MAX_LOGIN_FAILS_PER_USER) {
				lock(userLockKey(username), LOGIN_LOCK);
			}
		}
		if (StringUtils.hasText(ip)) {
			long fails = increment(ipFailKey(ip), LOGIN_WINDOW);
			if (fails >= MAX_LOGIN_FAILS_PER_IP) {
				lock(ipLockKey(ip), LOGIN_LOCK);
			}
		}
	}

	public void onLoginSuccess(String username, String ip) {
		if (StringUtils.hasText(username)) {
			redissonClient.getAtomicLong(userFailKey(username)).delete();
			redissonClient.getBucket(userLockKey(username)).delete();
		}
		if (StringUtils.hasText(ip)) {
			redissonClient.getAtomicLong(ipFailKey(ip)).delete();
		}
	}

	/**
	 * @return true if signup is allowed, false if rate limited
	 */
	public boolean tryConsumeSignup(String ip) {
		if (!StringUtils.hasText(ip)) {
			return true;
		}
		String key = "auth:signup:ip:" + ip;
		RAtomicLong counter = redissonClient.getAtomicLong(key);
		long count = counter.incrementAndGet();
		if (count == 1L) {
			counter.expire(SIGNUP_WINDOW);
		}
		return count <= MAX_SIGNUPS_PER_IP;
	}

	/**
	 * @return true if refresh is allowed, false if the user exceeded the per-minute limit
	 */
	public boolean tryConsumeRefresh(Long userId) {
		if (userId == null) {
			return false;
		}
		return increment("api:refresh:user:" + userId, REFRESH_WINDOW) <= MAX_REFRESHES_PER_USER;
	}

	/**
	 * X-Forwarded-For는 클라이언트가 위조할 수 있어 직접 읽지 않는다.
	 * 프록시 뒤 배포 시 server.forward-headers-strategy=native 가 신뢰 프록시의 헤더만 반영한다.
	 */
	public static String clientIp(HttpServletRequest request) {
		return request.getRemoteAddr();
	}

	public static String normalizeUsername(String username) {
		return username == null ? "" : username.trim().toLowerCase();
	}

	private long increment(String key, Duration ttl) {
		RAtomicLong counter = redissonClient.getAtomicLong(key);
		long value = counter.incrementAndGet();
		if (value == 1L) {
			counter.expire(ttl);
		}
		return value;
	}

	private void lock(String key, Duration ttl) {
		RBucket<String> bucket = redissonClient.getBucket(key);
		bucket.set("1", ttl.toSeconds(), TimeUnit.SECONDS);
	}

	private boolean exists(String key) {
		return redissonClient.getBucket(key).isExists();
	}

	private static String userFailKey(String username) {
		return "auth:login:fail:user:" + normalizeUsername(username);
	}

	private static String ipFailKey(String ip) {
		return "auth:login:fail:ip:" + ip;
	}

	private static String userLockKey(String username) {
		return "auth:login:lock:user:" + normalizeUsername(username);
	}

	private static String ipLockKey(String ip) {
		return "auth:login:lock:ip:" + ip;
	}
}
