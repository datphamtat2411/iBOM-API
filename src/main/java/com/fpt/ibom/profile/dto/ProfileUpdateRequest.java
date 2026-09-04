package com.fpt.ibom.profile.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
		@NotBlank @Size(max = 100) String profileName,
		@NotBlank @Size(max = 100) String firstName,
		@NotBlank @Size(max = 100) String lastName,
		@NotBlank @Size(max = 100) String jobTitle,
		@NotNull @DecimalMin(value = "0.0") BigDecimal yearsOfExperience,
		@NotBlank @Size(max = 4000) String personality,
		@NotBlank @Size(max = 4000) String technicalSummary,
		@NotNull @DecimalMin(value = "0") Long version) {
}
