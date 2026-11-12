package com.evcharge.reservation.ev.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.evcharge.reservation.ev.domain.User;
import com.evcharge.reservation.ev.repository.UserRepository;
import com.evcharge.reservation.ev.security.LoginUser;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

	private final UserRepository userRepository;

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		User user = userRepository.findByEmail(username)
				.orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username));

		return new LoginUser(user.getId(), user.getEmail(), user.getPassword());
	}
}
