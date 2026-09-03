package com.fpt.ibom.profile.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fpt.ibom.profile.entity.Profile;

public record ProfileDetailResponse(Long id, String profileName, String fullName, String firstName, String lastName,
		String jobTitle, String personality, String technicalSummary, String email, String phoneNumber, String address,
		BigDecimal yearsOfExperience, boolean hasPreviewed, long version, Instant createdAt, Instant updatedAt) {

	public ProfileDetailResponse(Long id, String profileName, String fullName, String jobTitle, String email,
			String phoneNumber, String address, BigDecimal yearsOfExperience, boolean hasPreviewed, long version,
			Instant createdAt, Instant updatedAt) {
		this(id, profileName, fullName, null, null, jobTitle, null, null, email, phoneNumber, address,
				yearsOfExperience, hasPreviewed, version, createdAt, updatedAt);
	}

	public static ProfileDetailResponse from(Profile profile) {
		return new ProfileDetailResponse(profile.getId(), profile.getProfileName(), profile.getFullName(),
				profile.getFirstName(), profile.getLastName(), profile.getJobTitle(), profile.getPersonality(),
				profile.getTechnicalSummary(), profile.getEmail(), profile.getPhoneNumber(), profile.getAddress(),
				profile.getYearsOfExperience(), profile.isHasPreviewed(), profile.getVersion(), profile.getCreatedAt(),
				profile.getUpdatedAt());
	}
}
