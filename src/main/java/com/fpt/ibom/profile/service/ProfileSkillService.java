package com.fpt.ibom.profile.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.master.entity.Skill;
import com.fpt.ibom.master.repository.SkillRepository;
import com.fpt.ibom.profile.dto.ProfileSkillMutationResponse;
import com.fpt.ibom.profile.dto.ProfileSkillRequest;
import com.fpt.ibom.profile.dto.ProfileSkillResponse;
import com.fpt.ibom.profile.dto.ProfileVersionResponse;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.entity.ProfileSkill;
import com.fpt.ibom.profile.repository.ProfileRepository;
import com.fpt.ibom.profile.repository.ProfileSkillRepository;
import jakarta.persistence.OptimisticLockException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileSkillService {
	private static final String PROFILE_SKILL_UNIQUE_CONSTRAINT = "uk_profile_skills_profile_skill";

	private final ProfileSkillRepository profileSkillRepository;
	private final ProfileRepository profileRepository;
	private final SkillRepository skillRepository;
	private final ProfileVersionService profileVersionService;
	private final Clock clock;

	public ProfileSkillService(ProfileSkillRepository profileSkillRepository, ProfileRepository profileRepository,
			SkillRepository skillRepository, ProfileVersionService profileVersionService, Clock clock) {
		this.profileSkillRepository = profileSkillRepository;
		this.profileRepository = profileRepository;
		this.skillRepository = skillRepository;
		this.profileVersionService = profileVersionService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<ProfileSkillResponse> list(Long userId, Long profileId) {
		findOwnedActiveProfile(userId, profileId);
		return profileSkillRepository.findByProfileId(profileId).stream()
				.sorted(Comparator.comparing(ProfileSkill::getExperienceYears, Comparator.reverseOrder())
						.thenComparing(profileSkill -> profileSkill.getSkill().getName(), String.CASE_INSENSITIVE_ORDER)
						.thenComparing(ProfileSkill::getId, Comparator.nullsLast(Comparator.naturalOrder())))
				.map(ProfileSkillResponse::from).toList();
	}

	@Transactional
	public ProfileSkillMutationResponse create(Long userId, Long profileId, ProfileSkillRequest request) {
		Profile profile = findOwnedActiveProfile(userId, profileId);
		CanonicalProfileSkill canonical = canonicalize(request);
		checkVersion(profile, request.version());
		Skill skill = findSkill(canonical.skillId());
		if (profileSkillRepository.existsByProfileIdAndSkillId(profileId, canonical.skillId())) {
			throw duplicateSkill();
		}
		try {
			long profileVersion = profileVersionService.advance(profile);
			ProfileSkill profileSkill = new ProfileSkill(profile, skill, canonical.experienceYears(), canonical.lastUsed());
			profileSkillRepository.saveAndFlush(profileSkill);
			ProfileSkillResponse response = ProfileSkillResponse.from(profileSkill);
			return new ProfileSkillMutationResponse(response, profileVersion);
		} catch (OptimisticLockingFailureException | OptimisticLockException exception) {
			throw versionConflict();
		} catch (DataIntegrityViolationException exception) {
			if (isProfileSkillUniqueConstraintViolation(exception)) {
				throw duplicateSkill();
			}
			throw exception;
		}
	}

	@Transactional
	public ProfileSkillMutationResponse update(Long userId, Long profileId, Long profileSkillId,
			ProfileSkillRequest request) {
		Profile profile = findOwnedActiveProfile(userId, profileId);
		ProfileSkill profileSkill = profileSkillRepository.findByIdAndProfileId(profileSkillId, profileId)
				.orElseThrow(this::profileSkillNotFound);
		CanonicalProfileSkill canonical = canonicalize(request);
		checkVersion(profile, request.version());
		Skill skill = findSkill(canonical.skillId());
		if (profileSkillRepository.existsByProfileIdAndSkillIdAndIdNot(profileId, canonical.skillId(), profileSkillId)) {
			throw duplicateSkill();
		}
		try {
			long profileVersion = profileVersionService.advance(profile);
			profileSkill.update(skill, canonical.experienceYears(), canonical.lastUsed());
			profileSkillRepository.saveAndFlush(profileSkill);
			ProfileSkillResponse response = ProfileSkillResponse.from(profileSkill);
			return new ProfileSkillMutationResponse(response, profileVersion);
		} catch (OptimisticLockingFailureException | OptimisticLockException exception) {
			throw versionConflict();
		} catch (DataIntegrityViolationException exception) {
			if (isProfileSkillUniqueConstraintViolation(exception)) {
				throw duplicateSkill();
			}
			throw exception;
		}
	}

	@Transactional
	public ProfileVersionResponse delete(Long userId, Long profileId, Long profileSkillId, Long version) {
		Profile profile = findOwnedActiveProfile(userId, profileId);
		ProfileSkill profileSkill = profileSkillRepository.findByIdAndProfileId(profileSkillId, profileId)
				.orElseThrow(this::profileSkillNotFound);
		checkVersion(profile, version);
		try {
			long profileVersion = profileVersionService.advance(profile);
			profileSkillRepository.delete(profileSkill);
			profileSkillRepository.flush();
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

	private CanonicalProfileSkill canonicalize(ProfileSkillRequest request) {
		if (request == null || request.skillId() == null || request.skillId() <= 0 || request.experienceYears() == null
				|| request.experienceYears().compareTo(BigDecimal.ZERO) < 0) {
			throw validationFailed();
		}
		if (request.lastUsed() != null && request.lastUsed().isAfter(LocalDate.now(clock))) {
			throw futureLastUsed();
		}
		return new CanonicalProfileSkill(request.skillId(), request.experienceYears(), request.lastUsed());
	}

	private Skill findSkill(Long skillId) {
		return skillRepository.findById(skillId).orElseThrow(this::skillNotFound);
	}

	private boolean isProfileSkillUniqueConstraintViolation(DataIntegrityViolationException exception) {
		Throwable cause = exception;
		while (cause != null) {
			if (cause instanceof ConstraintViolationException constraintViolation) {
				String constraintName = constraintViolation.getConstraintName();
				return PROFILE_SKILL_UNIQUE_CONSTRAINT.equals(constraintName)
						|| constraintName != null && constraintName.endsWith("." + PROFILE_SKILL_UNIQUE_CONSTRAINT);
			}
			cause = cause.getCause();
		}
		return false;
	}

	private ApiException profileNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.PROFILE_NOT_FOUND, "Profile not found");
	}

	private ApiException skillNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.SKILL_NOT_FOUND, "Skill not found");
	}

	private ApiException profileSkillNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.PROFILE_SKILL_NOT_FOUND, "Profile Skill not found");
	}

	private ApiException duplicateSkill() {
		return new ApiException(HttpStatus.CONFLICT, ErrorCode.PROFILE_SKILL_ALREADY_EXISTS,
				"Skill is already assigned to this Profile");
	}

	private ApiException futureLastUsed() {
		return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.PROFILE_SKILL_LAST_USED_IN_FUTURE,
				"Profile Skill last used date must not be in the future");
	}

	private ApiException validationFailed() {
		return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Validation failed");
	}

	private ApiException versionConflict() {
		return new ApiException(HttpStatus.CONFLICT, ErrorCode.PROFILE_VERSION_CONFLICT,
				"Profile was updated by another request");
	}

	private record CanonicalProfileSkill(Long skillId, BigDecimal experienceYears, LocalDate lastUsed) {
	}
}
