package com.evcharge.reservation.ev.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.evcharge.reservation.ev.domain.Favorite;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

	Optional<Favorite> findByUserIdAndStationId(Long userId, Long stationId);

	boolean existsByUserIdAndStationId(Long userId, Long stationId);

	@Query("""
			SELECT f FROM Favorite f
			JOIN FETCH f.station
			WHERE f.user.id = :userId
			ORDER BY f.id DESC
			""")
	List<Favorite> findByUserIdWithStation(@Param("userId") Long userId);
}
