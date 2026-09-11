package com.evcharge.reservation.ev.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reservations")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reservation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "charger_id", nullable = false)
	private Charger charger;

	@Column(nullable = false)
	private LocalDateTime startTime;

	@Column(nullable = false)
	private LocalDateTime endTime;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ReservationStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "reservation_type")
	private ReservationType type;

	@Column(name = "hold_expires_at")
	private LocalDateTime holdExpiresAt;

	@Column(name = "expiry_notified", nullable = false, columnDefinition = "boolean default false")
	@Builder.Default
	private boolean expiryNotified = false;

	@Version
	private Long version;

	public void cancel() {
		this.status = ReservationStatus.CANCELLED;
	}

	public void expire() {
		this.status = ReservationStatus.EXPIRED;
		this.expiryNotified = false;
	}

	public void markExpiryNotified() {
		this.expiryNotified = true;
	}
}
