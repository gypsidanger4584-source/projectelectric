package com.evcharge.reservation.ev.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString(exclude = "password")
public class SignupRequest {

	@NotBlank(message = "이메일은 필수입니다")
	@Email(message = "이메일 형식이 올바르지 않습니다")
	private String email;

	@NotBlank(message = "사용자 이름은 필수입니다")
	@Size(min = 2, max = 50, message = "사용자 이름은 2~50자여야 합니다")
	private String username;

	@NotBlank(message = "비밀번호는 필수입니다")
	@Size(min = 8, max = 72, message = "비밀번호는 8자 이상이어야 합니다")
	@Pattern(
			regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
			message = "비밀번호는 영문과 숫자를 각각 1자 이상 포함해야 합니다"
	)
	private String password;

	private String vehicleBrand;
	private String vehicleModel;
}
