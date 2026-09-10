package com.fpt.ibom.profile.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ProfileLanguageRequest(
		@NotNull @Positive Long languageId,
		@NotBlank String level,
		@NotNull @Min(0) Long version) {
}
