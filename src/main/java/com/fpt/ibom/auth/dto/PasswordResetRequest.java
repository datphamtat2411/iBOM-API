package com.fpt.ibom.auth.dto;

import com.fpt.ibom.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordResetRequest(
		@NotBlank @Email String email,
		@NotBlank @Pattern(regexp = "\\d{6}") String verificationCode,
		@NotBlank @StrongPassword String password) {
}
