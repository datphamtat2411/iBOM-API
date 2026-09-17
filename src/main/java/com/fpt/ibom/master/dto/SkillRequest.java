package com.fpt.ibom.master.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SkillRequest(
		@NotBlank @Size(max = 255) String name,
		@NotNull @Positive Long categoryId) {

	public SkillRequest {
		name = name == null ? null : name.trim();
	}
}
