package com.evcharge.reservation.ev.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.evcharge.reservation.ev.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

	@Query("""
			SELECT u FROM User u
			LEFT JOIN FETCH u.vehicle
			WHERE u.email = :email
			""")
	Optional<User> findByEmailWithVehicle(@Param("email") String email);

	@Query("""
			SELECT u FROM User u
			LEFT JOIN FETCH u.vehicle
			WHERE u.id = :id
			""")
	Optional<User> findWithVehicleById(@Param("id") Long id);

	boolean existsByEmail(String email);
}
