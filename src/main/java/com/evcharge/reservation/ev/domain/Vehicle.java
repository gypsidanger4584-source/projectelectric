package com.evcharge.reservation.ev.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "vehicles")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vehicle {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false, unique = true)
	private User user;

	private String brand;

	private String model;

	/** 총 배터리 용량(kWh) */
	private Double batteryCapacityKwh;

	/** 현재 충전율(%) */
	private Integer batteryPercent;

	/** 남은 주행 가능 거리(km) */
	private Integer rangeKm;

	/** 오늘 주행거리(km) */
	private Double todayDistanceKm;

	/** 전비(km/kWh) */
	private Double efficiency;

	private Boolean isCharging;

	@UpdateTimestamp
	@Column(nullable = false)
	private LocalDateTime updatedAt;
}
