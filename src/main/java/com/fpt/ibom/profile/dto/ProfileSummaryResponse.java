package com.fpt.ibom.profile.dto;

import java.time.Instant;

import com.fpt.ibom.profile.entity.Profile;

public record ProfileSummaryResponse(Long id, String profileName, String fullName, String jobTitle,
		Instant updatedAt) {

	public static ProfileSummaryResponse from(Profile profile) {
		return new ProfileSummaryResponse(profile.getId(), profile.getProfileName(), profile.getFullName(),
				profile.getJobTitle(), profile.getUpdatedAt());
	}
}
