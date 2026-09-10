package com.fpt.ibom.profile.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ProfileSkillRequest(
		@NotNull @Positive Long skillId,
		@NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal experienceYears,
		LocalDate lastUsed,
		@NotNull @Min(0) Long version) {
}
