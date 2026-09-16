package com.fpt.ibom.profile.service;

import java.util.List;
import java.util.Locale;

import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.master.entity.Language;
import com.fpt.ibom.master.repository.LanguageRepository;
import com.fpt.ibom.profile.dto.ProfileLanguageMutationResponse;
import com.fpt.ibom.profile.dto.ProfileLanguageRequest;
import com.fpt.ibom.profile.dto.ProfileLanguageResponse;
import com.fpt.ibom.profile.dto.ProfileVersionResponse;
import com.fpt.ibom.profile.entity.LanguageLevel;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.entity.ProfileLanguage;
import com.fpt.ibom.profile.repository.ProfileLanguageRepository;
import com.fpt.ibom.profile.repository.ProfileRepository;
import jakarta.persistence.OptimisticLockException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileLanguageService {
	private static final String PROFILE_LANGUAGE_UNIQUE_CONSTRAINT = "uk_profile_languages_profile_language";

	private final ProfileLanguageRepository profileLanguageRepository;
	private final ProfileRepository profileRepository;
	private final LanguageRepository languageRepository;
	private final ProfileVersionService profileVersionService;

	public ProfileLanguageService(ProfileLanguageRepository profileLanguageRepository, ProfileRepository profileRepository,
			LanguageRepository languageRepository, ProfileVersionService profileVersionService) {
		this.profileLanguageRepository = profileLanguageRepository;
		this.profileRepository = profileRepository;
		this.languageRepository = languageRepository;
		this.profileVersionService = profileVersionService;
	}

	@Transactional(readOnly = true)
	public List<ProfileLanguageResponse> list(Long userId, Long profileId) {
		findOwnedActiveProfile(userId, profileId);
		return profileLanguageRepository.findByProfileId(profileId).stream()
				.sorted(ProfileDisplayOrder.languageComparator())
				.map(ProfileLanguageResponse::from).toList();
	}

	@Transactional
	public ProfileLanguageMutationResponse create(Long userId, Long profileId, ProfileLanguageRequest request) {
		Profile profile = findOwnedActiveProfile(userId, profileId);
		CanonicalProfileLanguage canonical = canonicalize(request);
		checkVersion(profile, request.version());
		Language language = findLanguage(canonical.languageId());
		if (profileLanguageRepository.existsByProfileIdAndLanguageId(profileId, canonical.languageId())) {
			throw duplicateLanguage();
		}
		try {
			long profileVersion = profileVersionService.advance(profile);
			ProfileLanguage profileLanguage = new ProfileLanguage(profile, language, canonical.level());
			profileLanguageRepository.saveAndFlush(profileLanguage);
			ProfileLanguageResponse response = ProfileLanguageResponse.from(profileLanguage);
			return new ProfileLanguageMutationResponse(response, profileVersion);
		} catch (OptimisticLockingFailureException | OptimisticLockException exception) {
			throw versionConflict();
		} catch (DataIntegrityViolationException exception) {
			if (isProfileLanguageUniqueConstraintViolation(exception)) {
				throw duplicateLanguage();
			}
			throw exception;
		}
	}

	@Transactional
	public ProfileLanguageMutationResponse update(Long userId, Long profileId, Long profileLanguageId,
			ProfileLanguageRequest request) {
		Profile profile = findOwnedActiveProfile(userId, profileId);
		ProfileLanguage profileLanguage = profileLanguageRepository.findByIdAndProfileId(profileLanguageId, profileId)
				.orElseThrow(this::profileLanguageNotFound);
		CanonicalProfileLanguage canonical = canonicalize(request);
		checkVersion(profile, request.version());
		Language language = findLanguage(canonical.languageId());
		if (profileLanguageRepository.existsByProfileIdAndLanguageIdAndIdNot(profileId, canonical.languageId(),
				profileLanguageId)) {
			throw duplicateLanguage();
		}
		try {
			long profileVersion = profileVersionService.advance(profile);
			profileLanguage.update(language, canonical.level());
			profileLanguageRepository.saveAndFlush(profileLanguage);
			ProfileLanguageResponse response = ProfileLanguageResponse.from(profileLanguage);
			return new ProfileLanguageMutationResponse(response, profileVersion);
		} catch (OptimisticLockingFailureException | OptimisticLockException exception) {
			throw versionConflict();
		} catch (DataIntegrityViolationException exception) {
			if (isProfileLanguageUniqueConstraintViolation(exception)) {
				throw duplicateLanguage();
			}
			throw exception;
		}
	}

	@Transactional
	public ProfileVersionResponse delete(Long userId, Long profileId, Long profileLanguageId, Long version) {
		Profile profile = findOwnedActiveProfile(userId, profileId);
		ProfileLanguage profileLanguage = profileLanguageRepository.findByIdAndProfileId(profileLanguageId, profileId)
				.orElseThrow(this::profileLanguageNotFound);
		checkVersion(profile, version);
		try {
			long profileVersion = profileVersionService.advance(profile);
			profileLanguageRepository.delete(profileLanguage);
			profileLanguageRepository.flush();
			return new ProfileVersionResponse(profileVersion);
		} catch (OptimisticLockingFailureException | OptimisticLockException exception) {
			throw versionConflict();
		}
	}

	private Profile findOwnedActiveProfile(Long userId, Long profileId) {
		return profileRepository.findByIdAndUserIdAndDeletedAtIsNull(profileId, userId)
				.orElseThrow(this::profileNotFound);
	}

	private void checkVersion(Profile profile, Long expectedVersion) {
		if (expectedVersion == null || profile.getVersion() != expectedVersion) {
			throw versionConflict();
		}
	}

	private CanonicalProfileLanguage canonicalize(ProfileLanguageRequest request) {
		return new CanonicalProfileLanguage(request.languageId(), parseLevel(request.level()));
	}

	private LanguageLevel parseLevel(String value) {
		try {
			return LanguageLevel.valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException | NullPointerException exception) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.PROFILE_LANGUAGE_INVALID_LEVEL,
					"Profile Language level is invalid");
		}
	}

	private Language findLanguage(Long languageId) {
		return languageRepository.findById(languageId).orElseThrow(this::languageNotFound);
	}

	private boolean isProfileLanguageUniqueConstraintViolation(DataIntegrityViolationException exception) {
		Throwable cause = exception;
		while (cause != null) {
			if (cause instanceof ConstraintViolationException constraintViolation) {
				String constraintName = constraintViolation.getConstraintName();
				return PROFILE_LANGUAGE_UNIQUE_CONSTRAINT.equals(constraintName)
						|| constraintName != null && constraintName.endsWith("." + PROFILE_LANGUAGE_UNIQUE_CONSTRAINT);
			}
			cause = cause.getCause();
		}
		return false;
	}

	private ApiException profileNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.PROFILE_NOT_FOUND, "Profile not found");
	}

	private ApiException languageNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.LANGUAGE_NOT_FOUND, "Language not found");
	}

	private ApiException profileLanguageNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.PROFILE_LANGUAGE_NOT_FOUND,
				"Profile Language not found");
	}

	private ApiException duplicateLanguage() {
		return new ApiException(HttpStatus.CONFLICT, ErrorCode.PROFILE_LANGUAGE_ALREADY_EXISTS,
				"Language is already assigned to this Profile");
	}

	private ApiException versionConflict() {
		return new ApiException(HttpStatus.CONFLICT, ErrorCode.PROFILE_VERSION_CONFLICT,
				"Profile was updated by another request");
	}

	private record CanonicalProfileLanguage(Long languageId, LanguageLevel level) {
	}
}
