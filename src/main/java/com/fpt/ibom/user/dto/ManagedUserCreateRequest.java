package com.fpt.ibom.user.dto;

import com.fpt.ibom.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ManagedUserCreateRequest(
		@NotBlank @Email String email,
		@NotBlank @Size(max = 100) String username,
		@NotBlank @StrongPassword String password,
		@NotBlank @Pattern(regexp = "MEMBER|MANAGER|ADMIN") String role) {
}
