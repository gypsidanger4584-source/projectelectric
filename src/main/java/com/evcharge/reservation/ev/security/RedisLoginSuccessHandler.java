package com.evcharge.reservation.ev.security;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RedisLoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

	private final AuthRateLimitService authRateLimitService;

	public RedisLoginSuccessHandler(AuthRateLimitService authRateLimitService) {
		this.authRateLimitService = authRateLimitService;
		setDefaultTargetUrl("/");
		setAlwaysUseDefaultTargetUrl(true);
	}

	@Override
	public void onAuthenticationSuccess(
			HttpServletRequest request,
			HttpServletResponse response,
			Authentication authentication
	) throws IOException, ServletException {
		String username = AuthRateLimitService.normalizeUsername(request.getParameter("username"));
		String ip = AuthRateLimitService.clientIp(request);
		authRateLimitService.onLoginSuccess(username, ip);
		super.onAuthenticationSuccess(request, response, authentication);
	}
}
