package com.fpt.ibom.profile.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProjectRequest(
		@NotBlank @Size(max = 255) String name,
		@NotBlank String description,
		@NotNull LocalDate startDate,
		LocalDate endDate,
		@NotBlank String status,
		@NotBlank @Size(max = 255) String position,
		@NotNull @Min(1) Integer teamSize,
		@NotBlank String responsibilities,
		String programmingLanguages,
		String tools,
		@NotNull @Min(0) Long version) {
}
