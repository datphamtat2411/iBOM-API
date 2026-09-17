package com.fpt.ibom.profile.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.profile.dto.EducationMutationResponse;
import com.fpt.ibom.profile.dto.EducationRequest;
import com.fpt.ibom.profile.dto.EducationResponse;
import com.fpt.ibom.profile.dto.ProfileVersionResponse;
import com.fpt.ibom.profile.entity.Education;
import com.fpt.ibom.profile.entity.EducationStatus;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.repository.EducationRepository;
import jakarta.persistence.OptimisticLockException;
import org.springframework.http.HttpStatus;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EducationService {
	private final EducationRepository educationRepository;
	private final ProfileAccessService profileAccessService;
	private final ProfileVersionService profileVersionService;

	public EducationService(EducationRepository educationRepository, ProfileAccessService profileAccessService,
			ProfileVersionService profileVersionService) {
		this.educationRepository = educationRepository;
		this.profileAccessService = profileAccessService;
		this.profileVersionService = profileVersionService;
	}

	@Transactional(readOnly = true)
	public List<EducationResponse> list(UserPrincipal principal, Long profileId) {
		findAuthorizedProfile(principal, profileId);
		return educationRepository.findByProfileIdOrderByIdAsc(profileId).stream().map(EducationResponse::from).toList();
	}

	@Transactional
	public EducationMutationResponse create(UserPrincipal principal, Long profileId, EducationRequest request) {
		Profile profile = findAuthorizedProfile(principal, profileId);
		CanonicalEducation canonical = canonicalize(request);
		checkVersion(profile, request.version());
		try {
			long profileVersion = profileVersionService.advance(profile);
			Education education = new Education(profile, canonical.schoolName(), canonical.degree(), canonical.fieldOfStudy(),
					canonical.startDate(), canonical.endDate(), canonical.status());
			educationRepository.saveAndFlush(education);
			EducationResponse response = EducationResponse.from(education);
			return new EducationMutationResponse(response, profileVersion);
		} catch (OptimisticLockingFailureException | OptimisticLockException exception) {
			throw versionConflict();
		}
	}

	@Transactional
	public EducationMutationResponse update(UserPrincipal principal, Long profileId, Long educationId, EducationRequest request) {
		Profile profile = findAuthorizedProfile(principal, profileId);
		Education education = educationRepository.findByIdAndProfileId(educationId, profileId)
				.orElseThrow(this::educationNotFound);
		CanonicalEducation canonical = canonicalize(request);
		checkVersion(profile, request.version());
		try {
			long profileVersion = profileVersionService.advance(profile);
			education.update(canonical.schoolName(), canonical.degree(), canonical.fieldOfStudy(), canonical.startDate(),
					canonical.endDate(), canonical.status());
			educationRepository.saveAndFlush(education);
			EducationResponse response = EducationResponse.from(education);
			return new EducationMutationResponse(response, profileVersion);
		} catch (OptimisticLockingFailureException | OptimisticLockException exception) {
			throw versionConflict();
		}
	}

	@Transactional
	public ProfileVersionResponse delete(UserPrincipal principal, Long profileId, Long educationId, Long version) {
		Profile profile = findAuthorizedProfile(principal, profileId);
		Education education = educationRepository.findByIdAndProfileId(educationId, profileId)
				.orElseThrow(this::educationNotFound);
		checkVersion(profile, version);
		try {
			long profileVersion = profileVersionService.advance(profile);
			educationRepository.delete(education);
			educationRepository.flush();
			return new ProfileVersionResponse(profileVersion);
		} catch (OptimisticLockingFailureException | OptimisticLockException exception) {
			throw versionConflict();
		}
	}

	private Profile findAuthorizedProfile(UserPrincipal principal, Long profileId) {
		return profileAccessService.resolve(principal, profileId);
	}

	private void checkVersion(Profile profile, Long expectedVersion) {
		if (profile.getVersion() != expectedVersion) {
			throw versionConflict();
		}
	}

	private CanonicalEducation canonicalize(EducationRequest request) {
		EducationStatus status = parseStatus(request.status());
		LocalDate endDate = request.endDate();
		if (status == EducationStatus.ONGOING) {
			endDate = null;
		} else if (endDate == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.EDUCATION_END_DATE_REQUIRED,
					"Completed Education requires an end date");
		} else if (request.startDate().isAfter(endDate)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.EDUCATION_DATE_RANGE_INVALID,
					"Education start date must not be after end date");
		}
		return new CanonicalEducation(request.schoolName().trim(), request.degree().trim(), normalizeOptional(request.fieldOfStudy()),
				request.startDate(), endDate, status);
	}

	private EducationStatus parseStatus(String value) {
		try {
			return EducationStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException | NullPointerException exception) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.EDUCATION_INVALID_STATUS,
					"Education status is invalid");
		}
	}

	private String normalizeOptional(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private ApiException educationNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.EDUCATION_NOT_FOUND, "Education not found");
	}

	private ApiException versionConflict() {
		return new ApiException(HttpStatus.CONFLICT, ErrorCode.PROFILE_VERSION_CONFLICT,
				"Profile was updated by another request");
	}

	private record CanonicalEducation(String schoolName, String degree, String fieldOfStudy, LocalDate startDate,
			LocalDate endDate, EducationStatus status) {
	}
}
