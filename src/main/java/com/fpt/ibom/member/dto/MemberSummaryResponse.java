package com.fpt.ibom.member.dto;

import java.time.Instant;

import com.fpt.ibom.auth.entity.UserStatus;

public record MemberSummaryResponse(Long id, String username, String email, UserStatus status,
		Long activeProfileCount, Instant lastUpdatedAt) {
}
