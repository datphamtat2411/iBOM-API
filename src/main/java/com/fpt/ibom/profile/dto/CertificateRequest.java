package com.fpt.ibom.profile.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CertificateRequest(
		@NotBlank @Size(max = 255) String certificateName,
		@NotNull LocalDate issueDate,
		@NotNull @Min(0) Long version) {
}
