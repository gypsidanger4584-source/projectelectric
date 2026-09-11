package com.evcharge.reservation.ev.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.evcharge.reservation.ev.domain.User;
import com.evcharge.reservation.ev.domain.Vehicle;
import com.evcharge.reservation.ev.domain.VehicleImageCatalog;
import com.evcharge.reservation.ev.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class PageController {

	private final UserRepository userRepository;

	@org.springframework.beans.factory.annotation.Value("${kakao.maps.api.key}")
	private String kakaoMapsApiKey;

	@GetMapping("/")
	@Transactional(readOnly = true)
	public String mainPage(Model model) {
		bindLoggedInUser(model);
		model.addAttribute("userLat", null);
		model.addAttribute("userLng", null);
		model.addAttribute("kakaoApiKey", kakaoMapsApiKey);
		return "index";
	}

	@GetMapping("/login")
	public String loginPage() {
		return "login";
	}

	@GetMapping("/signup")
	public String signupPage() {
		return "signup";
	}

	@GetMapping("/map")
	@Transactional(readOnly = true)
	public String mapPage(Model model) {
		bindLoggedInUser(model);
		model.addAttribute("userLat", null);
		model.addAttribute("userLng", null);
		model.addAttribute("kakaoApiKey", kakaoMapsApiKey);
		return "map";
	}

	@GetMapping("/settings")
	@Transactional(readOnly = true)
	public String settingsPage(Model model) {
		bindLoggedInUser(model);
		return "setting";
	}

	private void bindLoggedInUser(Model model) {
		model.addAttribute("userId", null);
		model.addAttribute("vehicleBrand", null);
		model.addAttribute("vehicleModel", null);

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null
				|| !authentication.isAuthenticated()
				|| "anonymousUser".equals(authentication.getPrincipal())) {
			return;
		}

		String email = authentication.getName();
		userRepository.findByEmailWithVehicle(email).ifPresent(user -> {
			model.addAttribute("userId", user.getId());
			model.addAttribute("username", user.getUsername());
			bindVehicle(model, user);
		});
	}

	private void bindVehicle(Model model, User user) {
		Vehicle v = user.getVehicle();
		if (v == null) {
			return;
		}

		String brand = v.getBrand();
		String vehicleModel = v.getModel();
		model.addAttribute("vehicleBrand", brand);
		model.addAttribute("vehicleModel", vehicleModel);

		Map<String, Object> vehicle = new LinkedHashMap<>();
		vehicle.put("brand", brand);
		vehicle.put("model", vehicleModel);
		vehicle.put("batteryPercent", v.getBatteryPercent());
		vehicle.put("rangeKm", v.getRangeKm());
		vehicle.put("efficiency", v.getEfficiency());
		vehicle.put("todayDistanceKm", v.getTodayDistanceKm());
		vehicle.put("isCharging", v.getIsCharging());
		vehicle.put("imageUrl", VehicleImageCatalog.resolve(brand, vehicleModel));
		model.addAttribute("vehicle", vehicle);
	}
}
