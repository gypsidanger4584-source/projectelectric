package com.evcharge.reservation.ev.service;

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.evcharge.reservation.ev.domain.EvBatteryCatalog;
import com.evcharge.reservation.ev.domain.User;
import com.evcharge.reservation.ev.domain.Vehicle;
import com.evcharge.reservation.ev.dto.SignupRequest;
import com.evcharge.reservation.ev.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	@Transactional
	public void signup(SignupRequest request) {
		validatePassword(request.getPassword());

		if (userRepository.existsByEmail(request.getEmail())) {
			throw new IllegalStateException("이미 가입된 이메일입니다");
		}

		User user = User.builder()
				.email(request.getEmail())
				.username(request.getUsername())
				.password(passwordEncoder.encode(request.getPassword()))
				.build();

		String brand = request.getVehicleBrand();
		String model = request.getVehicleModel();
		if (brand == null || brand.isBlank() || model == null || model.isBlank()) {
			throw new IllegalStateException("차량을 선택해주세요");
		}
		brand = brand.trim();
		model = model.trim();

		double batteryCapacityKwh = EvBatteryCatalog.resolveCapacityKwh(brand, model);
		int batteryPercent = ThreadLocalRandom.current().nextInt(20, 101);
		double efficiency = EvBatteryCatalog.resolveRandomEfficiency(brand, model);
		int rangeKm = (int) Math.round(batteryCapacityKwh * (batteryPercent / 100.0) * efficiency);
		double todayDistanceKm = round1(ThreadLocalRandom.current().nextDouble() * 80.0);
		boolean isCharging = ThreadLocalRandom.current().nextBoolean();

		Vehicle vehicle = Vehicle.builder()
				.user(user)
				.brand(brand)
				.model(model)
				.batteryCapacityKwh(batteryCapacityKwh)
				.batteryPercent(batteryPercent)
				.efficiency(efficiency)
				.rangeKm(rangeKm)
				.todayDistanceKm(todayDistanceKm)
				.isCharging(isCharging)
				.build();

		user.assignVehicle(vehicle);
		userRepository.save(user);
	}

	private static void validatePassword(String password) {
		if (password == null || password.length() < 8 || password.length() > 72) {
			throw new IllegalStateException("비밀번호는 8자 이상이어야 합니다");
		}
		boolean hasLetter = password.chars().anyMatch(Character::isLetter);
		boolean hasDigit = password.chars().anyMatch(Character::isDigit);
		if (!hasLetter || !hasDigit) {
			throw new IllegalStateException("비밀번호는 영문과 숫자를 각각 1자 이상 포함해야 합니다");
		}
	}

	private static double round1(double value) {
		return Math.round(value * 10.0) / 10.0;
	}
}
