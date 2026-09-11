package com.fpt.ibom.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfileCopyRequest(@NotBlank @Size(max = 100) String profileName) {
}
