package com.fpt.ibom.profile.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ProfileVersionResponse(
		@JsonProperty("profileVersion") @JsonAlias("version") @NotNull @Min(0) Long profileVersion) {
}
