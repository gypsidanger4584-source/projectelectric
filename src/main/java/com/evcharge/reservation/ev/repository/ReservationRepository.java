package com.evcharge.reservation.ev.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.evcharge.reservation.ev.domain.Reservation;
import com.evcharge.reservation.ev.domain.ReservationStatus;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

	@Query("""
			select r from Reservation r
			join fetch r.charger c
			join fetch c.station
			join fetch r.user
			where r.user.id = :userId
			""")
	List<Reservation> findByUserId(@Param("userId") Long userId);

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

	@Query("""
			select r from Reservation r
			join fetch r.charger c
			join fetch c.station
			join fetch r.user
			where r.id = :id
			  and r.user.id = :userId
			""")
	Optional<Reservation> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

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

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update Reservation r
			set r.status = :expired,
			    r.expiryNotified = false
			where r.id = :id
			  and r.status = :pending
			""")
	int expireIfPending(
			@Param("id") Long id,
			@Param("expired") ReservationStatus expired,
			@Param("pending") ReservationStatus pending
	);

	/**
	 * 취소도 만료와 같은 조건부 UPDATE(CAS)로 처리한다. 이길 쪽은 {@code status}가 정한다.
	 * 엔티티 flush 방식은 읽기~flush 틈에 만료가 먼저 끝나도 version이 그대로라 EXPIRED를 CANCELLED로 덮어쓴다.
	 *
	 * @return 1이면 이 호출이 PENDING→CANCELLED로 바꿈, 0이면 이미 다른 경로가 처리함
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update Reservation r
			set r.status = :cancelled
			where r.id = :id
			  and r.user.id = :userId
			  and r.status = :pending
			""")
	int cancelIfPending(
			@Param("id") Long id,
			@Param("userId") Long userId,
			@Param("cancelled") ReservationStatus cancelled,
			@Param("pending") ReservationStatus pending
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
