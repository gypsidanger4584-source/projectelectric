package com.evcharge.reservation.ev.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.evcharge.reservation.ev.domain.UserSetting;

public interface UserSettingRepository extends JpaRepository<UserSetting, Long> {

	Optional<UserSetting> findByUserId(Long userId);
}
