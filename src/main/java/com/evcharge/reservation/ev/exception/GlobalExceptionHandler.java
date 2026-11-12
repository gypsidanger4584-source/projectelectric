package com.evcharge.reservation.ev.exception;

import java.time.Instant;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(TooManyRequestsException.class)
	public ResponseEntity<Map<String, Object>> handleTooManyRequests(TooManyRequestsException ex) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
				"timestamp", Instant.now().toString(),
				"status", 429,
				"error", "Too Many Requests",
				"message", ex.getMessage()
		));
	}

	@ExceptionHandler(ForbiddenException.class)
	public ResponseEntity<Map<String, Object>> handleForbidden(ForbiddenException ex) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
				"timestamp", Instant.now().toString(),
				"status", 403,
				"error", "Forbidden",
				"message", ex.getMessage()
		));
	}

	@ExceptionHandler(ConflictException.class)
	public ResponseEntity<Map<String, Object>> handleConflict(ConflictException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
				"timestamp", Instant.now().toString(),
				"status", 409,
				"error", "Conflict",
				"message", ex.getMessage()
		));
	}

	/** 엔티티 flush 경로의 @Version 충돌: 500 대신 "다른 요청이 먼저 처리됨" 409로 알린다. */
	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	public ResponseEntity<Map<String, Object>> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
		return handleConflict(new ConflictException("다른 요청이 먼저 처리되었습니다. 다시 시도해주세요."));
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
		if (PendingChargerUnique.isViolation(ex)) {
			return handleConflict(new ConflictException("이미 예약되었습니다"));
		}
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
				"timestamp", Instant.now().toString(),
				"status", 500,
				"error", "Internal Server Error",
				"message", "데이터 제약 위반"
		));
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
		HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
		if (status == null) {
			status = HttpStatus.INTERNAL_SERVER_ERROR;
		}
		return ResponseEntity.status(status).body(Map.of(
				"timestamp", Instant.now().toString(),
				"status", status.value(),
				"error", status.getReasonPhrase(),
				"message", ex.getReason() == null ? status.getReasonPhrase() : ex.getReason()
		));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
		return ResponseEntity.badRequest().body(Map.of(
				"timestamp", Instant.now().toString(),
				"status", 400,
				"error", "Bad Request",
				"message", ex.getMessage()
		));
	}
}
