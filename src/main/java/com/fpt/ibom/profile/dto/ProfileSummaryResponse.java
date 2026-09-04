package com.fpt.ibom.profile.dto;

import java.time.Instant;

import com.fpt.ibom.profile.entity.Profile;

public record ProfileSummaryResponse(Long id, String profileName, String firstName, String lastName, String jobTitle,
		Instant updatedAt) {

	public static ProfileSummaryResponse from(Profile profile) {
		return new ProfileSummaryResponse(profile.getId(), profile.getProfileName(), profile.getFirstName(),
				profile.getLastName(), profile.getJobTitle(), profile.getUpdatedAt());
	}
}
