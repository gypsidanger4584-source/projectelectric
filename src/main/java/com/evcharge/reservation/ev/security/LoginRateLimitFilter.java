package com.evcharge.reservation.ev.security;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LoginRateLimitFilter extends OncePerRequestFilter {

	private final AuthRateLimitService authRateLimitService;

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		if ("POST".equalsIgnoreCase(request.getMethod())
				&& "/login".equals(request.getServletPath())) {
			String username = AuthRateLimitService.normalizeUsername(request.getParameter("username"));
			String ip = AuthRateLimitService.clientIp(request);
			if (authRateLimitService.isLoginBlocked(username, ip)) {
				response.sendRedirect(request.getContextPath() + "/login?error=locked");
				return;
			}
		}
		filterChain.doFilter(request, response);
	}
}
