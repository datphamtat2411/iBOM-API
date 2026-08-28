package com.fpt.ibom.profile.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fpt.ibom.profile.entity.Profile;

public record ProfileDetailResponse(Long id, String profileName, String fullName, String jobTitle, String email,
		String phoneNumber, String address, BigDecimal yearsOfExperience, boolean hasPreviewed, long version,
		Instant createdAt, Instant updatedAt) {

	public static ProfileDetailResponse from(Profile profile) {
		return new ProfileDetailResponse(profile.getId(), profile.getProfileName(), profile.getFullName(),
				profile.getJobTitle(), profile.getEmail(), profile.getPhoneNumber(), profile.getAddress(),
				profile.getYearsOfExperience(), profile.isHasPreviewed(), profile.getVersion(), profile.getCreatedAt(),
				profile.getUpdatedAt());
	}
}
