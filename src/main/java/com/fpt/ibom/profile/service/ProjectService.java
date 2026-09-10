package com.fpt.ibom.profile.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.profile.dto.ProjectMutationResponse;
import com.fpt.ibom.profile.dto.ProjectRequest;
import com.fpt.ibom.profile.dto.ProjectResponse;
import com.fpt.ibom.profile.dto.ProfileVersionResponse;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.entity.Project;
import com.fpt.ibom.profile.entity.ProjectStatus;
import com.fpt.ibom.profile.repository.ProfileRepository;
import com.fpt.ibom.profile.repository.ProjectRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.OptimisticLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {
	private final ProjectRepository projectRepository;
	private final ProfileRepository profileRepository;
	private final EntityManager entityManager;

	public ProjectService(ProjectRepository projectRepository, ProfileRepository profileRepository,
			EntityManager entityManager) {
		this.projectRepository = projectRepository;
		this.profileRepository = profileRepository;
		this.entityManager = entityManager;
	}

	@Transactional(readOnly = true)
	public List<ProjectResponse> list(Long userId, Long profileId) {
		findOwnedActiveProfile(userId, profileId);
		return projectRepository.findByProfileIdInDisplayOrder(profileId).stream().map(ProjectResponse::from).toList();
	}

	@Transactional
	public ProjectMutationResponse create(Long userId, Long profileId, ProjectRequest request) {
		Profile profile = findOwnedActiveProfile(userId, profileId);
		CanonicalProject canonical = canonicalize(request);
		checkVersion(profile, request.version());
		try {
			lockAndInvalidate(profile);
			Project project = new Project(profile, canonical.name(), canonical.description(), canonical.startDate(),
					canonical.endDate(), canonical.status(), canonical.position(), canonical.teamSize(),
					canonical.responsibilities(), canonical.programmingLanguages(), canonical.tools());
			projectRepository.save(project);
			long profileVersion = flushAndReadVersion(profile);
			return new ProjectMutationResponse(ProjectResponse.from(project), profileVersion);
		} catch (OptimisticLockingFailureException | OptimisticLockException exception) {
			throw versionConflict();
		}
	}

	@Transactional
	public ProjectMutationResponse update(Long userId, Long profileId, Long projectId, ProjectRequest request) {
		Profile profile = findOwnedActiveProfile(userId, profileId);
		Project project = projectRepository.findByIdAndProfileId(projectId, profileId).orElseThrow(this::projectNotFound);
		CanonicalProject canonical = canonicalize(request);
		checkVersion(profile, request.version());
		try {
			lockAndInvalidate(profile);
			project.update(canonical.name(), canonical.description(), canonical.startDate(), canonical.endDate(),
					canonical.status(), canonical.position(), canonical.teamSize(), canonical.responsibilities(),
					canonical.programmingLanguages(), canonical.tools());
			projectRepository.save(project);
			long profileVersion = flushAndReadVersion(profile);
			return new ProjectMutationResponse(ProjectResponse.from(project), profileVersion);
		} catch (OptimisticLockingFailureException | OptimisticLockException exception) {
			throw versionConflict();
		}
	}

	@Transactional
	public ProfileVersionResponse delete(Long userId, Long profileId, Long projectId, Long version) {
		Profile profile = findOwnedActiveProfile(userId, profileId);
		Project project = projectRepository.findByIdAndProfileId(projectId, profileId).orElseThrow(this::projectNotFound);
		checkVersion(profile, version);
		try {
			lockAndInvalidate(profile);
			projectRepository.delete(project);
			return new ProfileVersionResponse(flushAndReadVersion(profile));
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

	private void lockAndInvalidate(Profile profile) {
		entityManager.lock(profile, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
		profile.invalidatePreview();
	}

	private long flushAndReadVersion(Profile profile) {
		long versionBeforeFlush = profile.getVersion();
		entityManager.flush();
		entityManager.refresh(profile);
		return Math.max(profile.getVersion(), versionBeforeFlush + 1);
	}

	private CanonicalProject canonicalize(ProjectRequest request) {
		String name = requiredText(request.name());
		String description = requiredText(request.description());
		String position = requiredText(request.position());
		String responsibilities = requiredText(request.responsibilities());
		if (request.startDate() == null || request.teamSize() == null || request.teamSize() < 1) {
			throw validationError();
		}
		ProjectStatus status = parseStatus(request.status());
		LocalDate endDate = request.endDate();
		if (status == ProjectStatus.ONGOING) {
			endDate = null;
		} else if (endDate == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.PROJECT_END_DATE_REQUIRED,
					"Completed Project requires an end date");
		} else if (request.startDate().isAfter(endDate)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.PROJECT_DATE_RANGE_INVALID,
					"Project start date must not be after end date");
		}
		return new CanonicalProject(name, description, request.startDate(), endDate, status, position, request.teamSize(),
				responsibilities, normalizeOptional(request.programmingLanguages()), normalizeOptional(request.tools()));
	}

	private ProjectStatus parseStatus(String value) {
		try {
			return ProjectStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException | NullPointerException exception) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.PROJECT_INVALID_STATUS,
					"Project status is invalid");
		}
	}

	private String requiredText(String value) {
		if (value == null) {
			throw validationError();
		}
		String normalized = value.trim();
		if (normalized.isBlank()) {
			throw validationError();
		}
		return normalized;
	}

	private String normalizeOptional(String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.trim();
		return normalized.isBlank() ? null : normalized;
	}

	private ApiException profileNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.PROFILE_NOT_FOUND, "Profile not found");
	}

	private ApiException projectNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.PROJECT_NOT_FOUND, "Project not found");
	}

	private ApiException validationError() {
		return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Validation failed");
	}

	private ApiException versionConflict() {
		return new ApiException(HttpStatus.CONFLICT, ErrorCode.PROFILE_VERSION_CONFLICT,
				"Profile was updated by another request");
	}

	private record CanonicalProject(String name, String description, LocalDate startDate, LocalDate endDate,
			ProjectStatus status, String position, Integer teamSize, String responsibilities, String programmingLanguages,
			String tools) {
	}
}
