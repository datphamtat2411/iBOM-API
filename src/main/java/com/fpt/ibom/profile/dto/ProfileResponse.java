package com.fpt.ibom.profile.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fpt.ibom.profile.entity.Profile;

public record ProfileResponse(Long id, Long userId, String profileName, String fullName, String jobTitle,
		String email, String phoneNumber, String address, BigDecimal yearsOfExperience, boolean hasPreviewed,
		long version, Instant createdAt, Instant updatedAt) {

	public static ProfileResponse from(Profile profile) {
		return new ProfileResponse(profile.getId(), profile.getUser().getId(), profile.getProfileName(), profile.getFullName(),
				profile.getJobTitle(), profile.getEmail(), profile.getPhoneNumber(), profile.getAddress(),
				profile.getYearsOfExperience(), profile.isHasPreviewed(), profile.getVersion(), profile.getCreatedAt(),
				profile.getUpdatedAt());
	}
}
