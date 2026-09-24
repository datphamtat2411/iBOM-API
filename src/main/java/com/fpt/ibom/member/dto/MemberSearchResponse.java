package com.fpt.ibom.member.dto;

import java.time.Instant;
import java.util.List;

import com.fpt.ibom.auth.entity.UserStatus;

public record MemberSearchResponse(Long id, String username, String email, UserStatus status,
		Long activeProfileCount, Instant lastUpdatedAt, List<MatchingProfileResponse> matchingProfiles) {
}
