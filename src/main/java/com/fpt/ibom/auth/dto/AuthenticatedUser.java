package com.fpt.ibom.auth.dto;

import com.fpt.ibom.auth.entity.UserRole;

public record AuthenticatedUser(Long id, String email, String username, UserRole role) {
}
