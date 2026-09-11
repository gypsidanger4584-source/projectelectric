package com.evcharge.reservation.ev.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
		name = "vehicle_connector_mapping",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_vehicle_connector",
				columnNames = {"brand", "model", "api_chger_type_code"}
		)
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleConnectorMapping {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String brand;

	@Column(nullable = false)
	private String model;

	@Column(name = "api_chger_type_code", nullable = false, length = 2)
	private String apiChgerTypeCode;
}
