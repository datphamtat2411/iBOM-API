package com.fpt.ibom.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PasswordResetCodeRequest(@NotBlank @Email String email) {
}
