package com.fpt.ibom.profile.service;

import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.profile.dto.ProfileRequest;
import com.fpt.ibom.profile.dto.ProfileResponse;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.repository.ProfileRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
	private final ProfileRepository profileRepository;
	private final UserAccountRepository userAccountRepository;

	public ProfileService(ProfileRepository profileRepository, UserAccountRepository userAccountRepository) {
		this.profileRepository = profileRepository;
		this.userAccountRepository = userAccountRepository;
	}

	@Transactional
	public ProfileResponse create(Long userId, ProfileRequest request) {
		String profileName = request.profileName().trim();
		if (profileRepository.existsByUserIdAndDeletedAtIsNullAndProfileNameIgnoreCase(userId, profileName)) {
			throw duplicateName();
		}
		UserAccount user = userAccountRepository.findById(userId).orElseThrow(this::invalidUser);
		Profile profile = new Profile(user, profileName, request.fullName().trim(), request.jobTitle().trim(),
				request.email().trim(), request.phoneNumber().trim(), request.address().trim(), request.yearsOfExperience());
		try {
			return ProfileResponse.from(profileRepository.saveAndFlush(profile));
		} catch (DataIntegrityViolationException exception) {
			throw duplicateName();
		}
	}

	private ApiException duplicateName() {
		return new ApiException(HttpStatus.CONFLICT, ErrorCode.PROFILE_NAME_ALREADY_EXISTS,
				"Profile name is already in use");
	}

	private ApiException invalidUser() {
		return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_INVALID_CREDENTIALS, "Invalid credentials");
	}
}
