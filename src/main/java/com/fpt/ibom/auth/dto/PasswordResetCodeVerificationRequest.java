package com.fpt.ibom.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordResetCodeVerificationRequest(
		@NotBlank @Email String email,
		@NotBlank @Pattern(regexp = "\\d{6}") String verificationCode) {
}
