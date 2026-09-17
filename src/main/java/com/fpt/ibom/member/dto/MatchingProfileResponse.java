package com.fpt.ibom.member.dto;

import java.time.Instant;
import java.util.List;

public record MatchingProfileResponse(Long id, String profileName, String firstName, String lastName, String jobTitle,
		Instant updatedAt, List<SkillSeniorityMatchResponse> matches) {
}
