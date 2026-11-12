package com.evcharge.reservation.ev.security;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import lombok.Getter;

@Getter
public class LoginUser extends User {

	private final Long id;

	public LoginUser(Long id, String email, String password) {
		super(email, password, List.of(new SimpleGrantedAuthority("ROLE_USER")));
		this.id = id;
	}
}
