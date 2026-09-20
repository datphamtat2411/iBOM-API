package com.fpt.ibom.user.dto;

import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;

public record UserSummaryResponse(Long id, String username, String email, UserRole role, UserStatus status) {
}
