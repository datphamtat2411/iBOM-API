package com.fpt.ibom.master.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SeniorityMutationRequest(
		@NotBlank String name,
		@NotNull @DecimalMin(value = "0.0", inclusive = true) @Digits(integer = 3, fraction = 2) BigDecimal fromExperience,
		@DecimalMin(value = "0.0", inclusive = true) @Digits(integer = 3, fraction = 2) BigDecimal toExperience) {
}
