package com.evcharge.reservation.ev.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.evcharge.reservation.ev.domain.Vehicle;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
}
