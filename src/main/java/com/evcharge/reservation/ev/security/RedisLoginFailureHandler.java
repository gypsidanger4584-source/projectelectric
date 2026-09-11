package com.evcharge.reservation.ev.security;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RedisLoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

	private final AuthRateLimitService authRateLimitService;

	public RedisLoginFailureHandler(AuthRateLimitService authRateLimitService) {
		this.authRateLimitService = authRateLimitService;
		setDefaultFailureUrl("/login?error");
	}

	@Override
	public void onAuthenticationFailure(
			HttpServletRequest request,
			HttpServletResponse response,
			AuthenticationException exception
	) throws IOException, ServletException {
		String username = AuthRateLimitService.normalizeUsername(request.getParameter("username"));
		String ip = AuthRateLimitService.clientIp(request);
		authRateLimitService.onLoginFailure(username, ip);

		if (authRateLimitService.isLoginBlocked(username, ip)) {
			getRedirectStrategy().sendRedirect(request, response, "/login?error=locked");
			return;
		}
		super.onAuthenticationFailure(request, response, exception);
	}
}
