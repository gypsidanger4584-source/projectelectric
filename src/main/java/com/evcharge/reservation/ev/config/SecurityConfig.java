package com.evcharge.reservation.ev.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.evcharge.reservation.ev.security.LoginRateLimitFilter;
import com.evcharge.reservation.ev.security.RedisLoginFailureHandler;
import com.evcharge.reservation.ev.security.RedisLoginSuccessHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	public PasswordEncoder passwordEncoder() {
		// strength 12 (기본 10보다 느려서 브루트포스 비용↑)
		return new BCryptPasswordEncoder(12);
	}

	/** Security 체인에서만 쓰도록 서블릿 컨테이너 자동 등록 비활성 */
	@Bean
	public FilterRegistrationBean<LoginRateLimitFilter> loginRateLimitFilterRegistration(
			LoginRateLimitFilter filter
	) {
		FilterRegistrationBean<LoginRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setEnabled(false);
		return registration;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			LoginRateLimitFilter loginRateLimitFilter,
			RedisLoginFailureHandler loginFailureHandler,
			RedisLoginSuccessHandler loginSuccessHandler
	) throws Exception {
		http
				.csrf(csrf -> csrf.ignoringRequestMatchers("/api/stations/**", "/api/**"))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(
								"/",
								"/login",
								"/signup",
								"/map",
								"/settings",
								"/css/**",
								"/js/**",
								"/images/**",
								"/static/**"
						).permitAll()
						.requestMatchers("/api/stations/**").permitAll()
						.anyRequest().authenticated()
				)
				.sessionManagement(session -> session
						.sessionFixation(fixation -> fixation.changeSessionId())
				)
				.formLogin(form -> form
						.loginPage("/login")
						.loginProcessingUrl("/login")
						.usernameParameter("username")
						.passwordParameter("password")
						.successHandler(loginSuccessHandler)
						.failureHandler(loginFailureHandler)
						.permitAll()
				)
				.logout(logout -> logout
						.logoutUrl("/logout")
						.logoutSuccessUrl("/login?logout")
						.invalidateHttpSession(true)
						.clearAuthentication(true)
						.deleteCookies("JSESSIONID")
						.permitAll()
				)
				.addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}
}
