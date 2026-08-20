package com.fpt.ibom.auth.dto;

import com.fpt.ibom.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
		@NotBlank String currentPassword,
		@NotBlank @StrongPassword String newPassword) {
}
