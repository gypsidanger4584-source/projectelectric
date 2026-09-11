package com.evcharge.reservation.ev.controller;

import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.evcharge.reservation.ev.dto.SignupRequest;
import com.evcharge.reservation.ev.security.AuthRateLimitService;
import com.evcharge.reservation.ev.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class SignupController {

	private final UserService userService;
	private final AuthRateLimitService authRateLimitService;

	@PostMapping("/signup")
	public String signup(
			@Valid @ModelAttribute SignupRequest request,
			BindingResult bindingResult,
			HttpServletRequest httpRequest,
			RedirectAttributes redirectAttributes
	) {
		if (bindingResult.hasErrors()) {
			String message = bindingResult.getFieldErrors().stream()
					.findFirst()
					.map(err -> err.getDefaultMessage())
					.orElse("입력값을 확인해주세요.");
			redirectAttributes.addFlashAttribute("error", message);
			return "redirect:/signup";
		}

		String ip = AuthRateLimitService.clientIp(httpRequest);
		if (!authRateLimitService.tryConsumeSignup(ip)) {
			redirectAttributes.addFlashAttribute("error", "가입 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
			return "redirect:/signup";
		}
		try {
			userService.signup(request);
			return "redirect:/login";
		} catch (IllegalStateException e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
			return "redirect:/signup";
		}
	}
}
