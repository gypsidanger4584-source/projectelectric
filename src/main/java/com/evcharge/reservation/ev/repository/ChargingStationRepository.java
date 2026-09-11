package com.evcharge.reservation.ev.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.evcharge.reservation.ev.domain.ChargingStation;

public interface ChargingStationRepository extends JpaRepository<ChargingStation, Long> {

	Optional<ChargingStation> findByStatId(String statId);

	@Query("""
			SELECT s FROM ChargingStation s
			WHERE s.latitude BETWEEN :minLat AND :maxLat
			  AND s.longitude BETWEEN :minLng AND :maxLng
			""")
	List<ChargingStation> findInBoundingBox(
			@Param("minLat") double minLat,
			@Param("maxLat") double maxLat,
			@Param("minLng") double minLng,
			@Param("maxLng") double maxLng
	);

	@Query("""
			SELECT s.statId FROM ChargingStation s
			WHERE s.latitude BETWEEN :minLat AND :maxLat
			  AND s.longitude BETWEEN :minLng AND :maxLng
			  AND s.statId IS NOT NULL
			""")
	List<String> findStatIdsInBoundingBox(
			@Param("minLat") double minLat,
			@Param("maxLat") double maxLat,
			@Param("minLng") double minLng,
			@Param("maxLng") double maxLng
	);
}
