package com.evcharge.reservation.ev.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.evcharge.reservation.ev.domain.Charger;
import com.evcharge.reservation.ev.domain.ChargerStatus;

import jakarta.persistence.LockModeType;

public interface ChargerRepository extends JpaRepository<Charger, Long> {

	List<Charger> findByStationId(Long stationId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT c FROM Charger c WHERE c.station.id = :stationId AND c.chgerId = :chgerId")
	Optional<Charger> findByStationIdAndChgerIdForUpdate(
			@Param("stationId") Long stationId,
			@Param("chgerId") String chgerId
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT c FROM Charger c JOIN FETCH c.station WHERE c.id = :id")
	Optional<Charger> findByIdForUpdate(@Param("id") Long id);

	@Query("""
			SELECT c FROM Charger c
			JOIN FETCH c.station s
			WHERE s.id IN :stationIds
			  AND (c.delYn IS NULL OR c.delYn <> 'Y')
			""")
	List<Charger> findActiveByStationIdIn(@Param("stationIds") Collection<Long> stationIds);

	@Query("""
			SELECT c FROM Charger c
			WHERE c.station.id = :stationId
			  AND (c.delYn IS NULL OR c.delYn <> 'Y')
			ORDER BY c.chgerId ASC
			""")
	List<Charger> findActiveByStationId(@Param("stationId") Long stationId);

	@Query("""
			SELECT c FROM Charger c
			JOIN FETCH c.station s
			WHERE s.id = :stationId
			  AND c.status = :status
			  AND (c.delYn IS NULL OR c.delYn <> 'Y')
			ORDER BY c.chgerId ASC
			""")
	List<Charger> findByStationIdAndStatus(
			@Param("stationId") Long stationId,
			@Param("status") ChargerStatus status
	);
}
