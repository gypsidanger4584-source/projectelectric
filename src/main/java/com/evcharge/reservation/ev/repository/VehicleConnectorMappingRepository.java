package com.evcharge.reservation.ev.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.evcharge.reservation.ev.domain.VehicleConnectorMapping;

public interface VehicleConnectorMappingRepository extends JpaRepository<VehicleConnectorMapping, Long> {

	List<VehicleConnectorMapping> findByBrandAndModel(String brand, String model);
}
