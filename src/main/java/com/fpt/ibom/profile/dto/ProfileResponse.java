package com.fpt.ibom.profile.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fpt.ibom.profile.entity.Profile;

public record ProfileResponse(Long id, Long userId, String profileName, String firstName, String lastName,
		String jobTitle, BigDecimal yearsOfExperience, String personality, String technicalSummary,
		boolean hasPreviewed, long version, Instant createdAt, Instant updatedAt, Instant lastExportedAt,
		Long preferredFileNameFormatId) {

	public static ProfileResponse from(Profile profile) {
		return new ProfileResponse(profile.getId(), profile.getUser().getId(), profile.getProfileName(),
				profile.getFirstName(), profile.getLastName(), profile.getJobTitle(), profile.getYearsOfExperience(),
				profile.getPersonality(), profile.getTechnicalSummary(), profile.isHasPreviewed(), profile.getVersion(),
				profile.getCreatedAt(), profile.getUpdatedAt(), profile.getLastExportedAt(),
				profile.getPreferredFileNameFormat() == null ? null : profile.getPreferredFileNameFormat().getId());
	}
}
