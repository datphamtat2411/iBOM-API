package com.fpt.ibom.auth.security;

import com.fpt.ibom.auth.entity.UserRole;

public record UserPrincipal(Long userId, String email, String username, UserRole role) {
}
