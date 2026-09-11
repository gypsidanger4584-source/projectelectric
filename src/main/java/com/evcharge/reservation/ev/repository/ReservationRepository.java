package com.evcharge.reservation.ev.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.evcharge.reservation.ev.domain.Reservation;
import com.evcharge.reservation.ev.domain.ReservationStatus;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

	List<Reservation> findByUserId(Long userId);

	@Query("""
			select r from Reservation r
			where r.user.id = :userId
			  and r.status = :status
			  and r.charger.id in :chargerIds
			""")
	List<Reservation> findByUserIdAndStatusAndChargerIdIn(
			@Param("userId") Long userId,
			@Param("status") ReservationStatus status,
			@Param("chargerIds") Collection<Long> chargerIds
	);

	Optional<Reservation> findByIdAndUserId(Long id, Long userId);

	boolean existsByUserIdAndChargerIdAndStatus(Long userId, Long chargerId, ReservationStatus status);

	boolean existsByCharger_IdAndStatus(Long chargerId, ReservationStatus status);

	@Query("""
			select r from Reservation r
			join fetch r.charger c
			join fetch c.station
			join fetch r.user
			where r.status = :status
			  and r.holdExpiresAt is not null
			  and r.holdExpiresAt <= :now
			""")
	List<Reservation> findExpiredPending(
			@Param("status") ReservationStatus status,
			@Param("now") LocalDateTime now
	);

	@Query("""
			select r from Reservation r
			join fetch r.charger c
			join fetch c.station
			where r.user.id = :userId
			  and r.status = :status
			  and r.expiryNotified = false
			order by r.holdExpiresAt desc
			""")
	List<Reservation> findUnnotifiedExpiredByUserId(
			@Param("userId") Long userId,
			@Param("status") ReservationStatus status
	);
}
