package com.fpt.ibom.member.dto;

import java.time.Instant;

public record MatchingProfileResponse(Long id, String profileName, String firstName, String lastName, String jobTitle,
		Instant updatedAt) {
}
