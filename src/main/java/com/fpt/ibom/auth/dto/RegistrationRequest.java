package com.fpt.ibom.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(
		@NotBlank @Email String email,
		@NotBlank @Size(max = 100) String username,
		@NotBlank @Size(min = 8, max = 72) String password,
		@NotBlank @Pattern(regexp = "\\d{6}") String verificationCode) {
}
