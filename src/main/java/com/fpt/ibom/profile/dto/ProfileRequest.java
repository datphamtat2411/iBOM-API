package com.fpt.ibom.profile.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProfileRequest(
		@NotBlank @Size(max = 100) String profileName,
		@NotBlank String fullName,
		@NotBlank String jobTitle,
		@NotBlank @Email String email,
		@NotBlank @Size(max = 50) String phoneNumber,
		@NotBlank @Size(max = 500) String address,
		@NotNull @DecimalMin(value = "0.0") BigDecimal yearsOfExperience) {
}
