package com.fpt.ibom.member.dto;

import java.time.Instant;
import java.util.List;

import com.fpt.ibom.profile.dto.ProfileLanguageResponse;

public record MemberLanguageSearchProfileResponse(Long id, String profileName, String firstName, String lastName,
		String jobTitle, Instant updatedAt, List<ProfileLanguageResponse> matchingLanguages) {
}
