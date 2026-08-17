package com.fpt.ibom.auth.dto;

public record LoginResponse(String accessToken, AuthenticatedUser user) {
}
