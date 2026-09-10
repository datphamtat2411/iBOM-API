package com.fpt.ibom.profile.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EducationRequest(
		@NotBlank @Size(max = 255) String schoolName,
		@NotBlank @Size(max = 255) String degree,
		@Size(max = 255) String fieldOfStudy,
		@NotNull LocalDate startDate,
		LocalDate endDate,
		@NotBlank String status,
		@NotNull @Min(0) Long version) {
}
