package com.fpt.ibom.profile.service;

import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.profile.dto.ProfileRequest;
import com.fpt.ibom.profile.dto.ProfileUpdateRequest;
import com.fpt.ibom.profile.dto.ProfileResponse;
import com.fpt.ibom.profile.dto.ProfileDetailResponse;
import com.fpt.ibom.profile.dto.ProfileSummaryResponse;
import com.fpt.ibom.profile.entity.Profile;
import java.util.List;
import java.time.Instant;
import jakarta.persistence.OptimisticLockException;
import com.fpt.ibom.profile.repository.ProfileRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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

	@Transactional(readOnly = true)
	public List<ProfileSummaryResponse> list(Long userId) {
		return profileRepository.findByUserIdAndDeletedAtIsNullOrderByUpdatedAtDescIdDesc(userId).stream()
				.map(ProfileSummaryResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public ProfileDetailResponse get(Long userId, Long profileId) {
		return profileRepository.findByIdAndUserIdAndDeletedAtIsNull(profileId, userId)
				.map(ProfileDetailResponse::from).orElseThrow(this::profileNotFound);
	}

	@Transactional
	public ProfileDetailResponse update(Long userId, Long profileId, ProfileUpdateRequest request) {
		Profile profile = profileRepository.findByIdAndUserIdAndDeletedAtIsNull(profileId, userId)
				.orElseThrow(this::profileNotFound);
		if (profile.getVersion() != request.version()) {
			throw versionConflict();
		}
		String profileName = request.profileName().trim();
		if (profileRepository.existsByUserIdAndDeletedAtIsNullAndProfileNameIgnoreCaseAndIdNot(userId, profileName,
				profileId)) {
			throw duplicateName();
		}
		profile.update(profileName, request.firstName().trim(), request.lastName().trim(), request.jobTitle().trim(),
				request.yearsOfExperience(), request.personality().trim(), request.technicalSummary().trim());
		try {
			return ProfileDetailResponse.from(profileRepository.saveAndFlush(profile));
		} catch (ObjectOptimisticLockingFailureException | OptimisticLockException exception) {
			throw versionConflict();
		} catch (DataIntegrityViolationException exception) {
			throw duplicateName();
		}
	}

	@Transactional
	public void delete(Long userId, Long profileId) {
		userAccountRepository.findByIdForUpdate(userId).orElseThrow(this::invalidUser);
		Profile profile = profileRepository.findByIdAndUserIdAndDeletedAtIsNull(profileId, userId)
				.orElseThrow(this::profileNotFound);
		if (profileRepository.countByUserIdAndDeletedAtIsNull(userId) <= 1) {
			throw lastActiveProfileCannotDelete();
		}
		profile.softDelete(Instant.now());
		profileRepository.saveAndFlush(profile);
	}

	private ApiException duplicateName() {
		return new ApiException(HttpStatus.CONFLICT, ErrorCode.PROFILE_NAME_ALREADY_EXISTS,
				"Profile name is already in use");
	}

	private ApiException invalidUser() {
		return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_INVALID_CREDENTIALS, "Invalid credentials");
	}

	private ApiException profileNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.PROFILE_NOT_FOUND, "Profile not found");
	}

	private ApiException versionConflict() {
		return new ApiException(HttpStatus.CONFLICT, ErrorCode.PROFILE_VERSION_CONFLICT,
				"Profile was updated by another request");
	}

	private ApiException lastActiveProfileCannotDelete() {
		return new ApiException(HttpStatus.CONFLICT, ErrorCode.PROFILE_LAST_ACTIVE_CANNOT_DELETE,
				"The last active Profile cannot be deleted");
	}
}
