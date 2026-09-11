package com.evcharge.reservation.ev.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
		name = "user_settings",
		uniqueConstraints = @UniqueConstraint(name = "uk_user_settings_user", columnNames = "user_id")
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSetting {

	public static final double MAX_DISTANCE_KM = 3.0;
	public static final double DEFAULT_DISTANCE_KM = 3.0;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, unique = true)
	private User user;

	/** ALL | SLOW | FAST */
	@Column(name = "speed_priority", nullable = false, length = 16)
	@Builder.Default
	private String speedPriority = "FAST";

	/** ALL | FREE */
	@Column(nullable = false, length = 16)
	@Builder.Default
	private String parking = "ALL";

	/** ALL | A0 | B0 | ... */
	@Column(nullable = false, length = 8)
	@Builder.Default
	private String place = "ALL";

	@Column(name = "external_open_only", nullable = false)
	@Builder.Default
	private boolean externalOpenOnly = false;

	@Column(name = "max_distance_km", nullable = false)
	@Builder.Default
	private Double maxDistanceKm = DEFAULT_DISTANCE_KM;

	public static UserSetting defaultsFor(User user) {
		return UserSetting.builder()
				.user(user)
				.speedPriority("FAST")
				.parking("ALL")
				.place("ALL")
				.externalOpenOnly(false)
				.maxDistanceKm(DEFAULT_DISTANCE_KM)
				.build();
	}

	public void apply(
			String speedPriority,
			String parking,
			String place,
			boolean externalOpenOnly,
			Double maxDistanceKm
	) {
		this.speedPriority = normalizeSpeed(speedPriority);
		this.parking = normalizeParking(parking);
		this.place = normalizePlace(place);
		this.externalOpenOnly = externalOpenOnly;
		this.maxDistanceKm = clampDistance(maxDistanceKm);
	}

	public static String normalizeSpeed(String value) {
		if (value == null || value.isBlank()) {
			return "FAST";
		}
		String v = value.trim().toUpperCase();
		if ("ALL".equals(v)) {
			return "ALL";
		}
		if ("SLOW".equals(v)) {
			return "SLOW";
		}
		return "FAST";
	}

	public static String normalizeParking(String value) {
		if (value == null || value.isBlank()) {
			return "ALL";
		}
		String v = value.trim().toUpperCase();
		return "FREE".equals(v) ? "FREE" : "ALL";
	}

	public static String normalizePlace(String value) {
		if (value == null || value.isBlank()) {
			return "ALL";
		}
		return value.trim().toUpperCase();
	}

	public static double clampDistance(Double value) {
		if (value == null || value <= 0) {
			return DEFAULT_DISTANCE_KM;
		}
		return Math.min(value, MAX_DISTANCE_KM);
	}
}
