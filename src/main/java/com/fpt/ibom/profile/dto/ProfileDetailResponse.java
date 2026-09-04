package com.fpt.ibom.profile.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fpt.ibom.profile.entity.Profile;

public record ProfileDetailResponse(Long id, String profileName, String firstName, String lastName, String jobTitle,
		BigDecimal yearsOfExperience, String personality, String technicalSummary, boolean hasPreviewed, long version,
		Instant createdAt, Instant updatedAt) {

	public static ProfileDetailResponse from(Profile profile) {
		return new ProfileDetailResponse(profile.getId(), profile.getProfileName(), profile.getFirstName(),
				profile.getLastName(), profile.getJobTitle(), profile.getYearsOfExperience(), profile.getPersonality(),
				profile.getTechnicalSummary(), profile.isHasPreviewed(), profile.getVersion(), profile.getCreatedAt(),
				profile.getUpdatedAt());
	}
}
