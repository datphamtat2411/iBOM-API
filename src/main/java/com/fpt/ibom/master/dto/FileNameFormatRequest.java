package com.fpt.ibom.master.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FileNameFormatRequest(
		@NotBlank @Size(max = 255) String name,
		@NotBlank @Size(max = 1000) String pattern) {
}
