package com.fpt.ibom.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UserStatusUpdateRequest(
		@NotNull
		@Pattern(regexp = "ACTIVE|INACTIVE")
		String status) {
}
