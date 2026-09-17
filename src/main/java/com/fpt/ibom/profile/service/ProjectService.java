package com.fpt.ibom.profile.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.profile.dto.ProjectMutationResponse;
import com.fpt.ibom.profile.dto.ProjectRequest;
import com.fpt.ibom.profile.dto.ProjectResponse;
import com.fpt.ibom.profile.dto.ProfileVersionResponse;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.entity.Project;
import com.fpt.ibom.profile.entity.ProjectStatus;
import com.fpt.ibom.profile.repository.ProjectRepository;
import jakarta.persistence.OptimisticLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {
	private final ProjectRepository projectRepository;
	private final ProfileAccessService profileAccessService;
	private final ProfileVersionService profileVersionService;

	public ProjectService(ProjectRepository projectRepository, ProfileAccessService profileAccessService,
			ProfileVersionService profileVersionService) {
		this.projectRepository = projectRepository;
		this.profileAccessService = profileAccessService;
		this.profileVersionService = profileVersionService;
	}

	@Transactional(readOnly = true)
	public List<ProjectResponse> list(UserPrincipal principal, Long profileId) {
		findAuthorizedProfile(principal, profileId);
		return projectRepository.findByProfileIdInDisplayOrder(profileId).stream().map(ProjectResponse::from).toList();
	}

	@Transactional
	public ProjectMutationResponse create(UserPrincipal principal, Long profileId, ProjectRequest request) {
		Profile profile = findAuthorizedProfile(principal, profileId);
		CanonicalProject canonical = canonicalize(request);
		checkVersion(profile, request.version());
		try {
			long profileVersion = profileVersionService.advance(profile);
			Project project = new Project(profile, canonical.name(), canonical.description(), canonical.startDate(),
					canonical.endDate(), canonical.status(), canonical.position(), canonical.teamSize(),
					canonical.responsibilities(), canonical.programmingLanguages(), canonical.tools());
			projectRepository.saveAndFlush(project);
			ProjectResponse response = ProjectResponse.from(project);
			return new ProjectMutationResponse(response, profileVersion);
		} catch (OptimisticLockingFailureException | OptimisticLockException exception) {
			throw versionConflict();
		}
	}

	@Transactional
	public ProjectMutationResponse update(UserPrincipal principal, Long profileId, Long projectId, ProjectRequest request) {
		Profile profile = findAuthorizedProfile(principal, profileId);
		Project project = projectRepository.findByIdAndProfileId(projectId, profileId).orElseThrow(this::projectNotFound);
		CanonicalProject canonical = canonicalize(request);
		checkVersion(profile, request.version());
		try {
			long profileVersion = profileVersionService.advance(profile);
			project.update(canonical.name(), canonical.description(), canonical.startDate(), canonical.endDate(),
					canonical.status(), canonical.position(), canonical.teamSize(), canonical.responsibilities(),
					canonical.programmingLanguages(), canonical.tools());
			projectRepository.saveAndFlush(project);
			ProjectResponse response = ProjectResponse.from(project);
			return new ProjectMutationResponse(response, profileVersion);
		} catch (OptimisticLockingFailureException | OptimisticLockException exception) {
			throw versionConflict();
		}
	}

	@Transactional
	public ProfileVersionResponse delete(UserPrincipal principal, Long profileId, Long projectId, Long version) {
		Profile profile = findAuthorizedProfile(principal, profileId);
		Project project = projectRepository.findByIdAndProfileId(projectId, profileId).orElseThrow(this::projectNotFound);
		checkVersion(profile, version);
		try {
			long profileVersion = profileVersionService.advance(profile);
			projectRepository.delete(project);
			projectRepository.flush();
			return new ProfileVersionResponse(profileVersion);
		} catch (OptimisticLockingFailureException | OptimisticLockException exception) {
			throw versionConflict();
		}
	}

	private Profile findAuthorizedProfile(UserPrincipal principal, Long profileId) {
		return profileAccessService.resolve(principal, profileId);
	}

	private void checkVersion(Profile profile, Long expectedVersion) {
		if (expectedVersion == null || profile.getVersion() != expectedVersion) {
			throw versionConflict();
		}
	}

	private CanonicalProject canonicalize(ProjectRequest request) {
		String name = requiredText(request.name());
		String description = requiredText(request.description());
		String position = requiredText(request.position());
		if (request.teamSize() != null && request.teamSize() < 1) {
			throw validationError();
		}
		ProjectStatus status = parseStatus(request.status());
		LocalDate endDate = request.endDate();
		if (status == ProjectStatus.ONGOING) {
			endDate = null;
		} else if (endDate == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.PROJECT_END_DATE_REQUIRED,
					"Completed Project requires an end date");
		} else if (request.startDate() != null && request.startDate().isAfter(endDate)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.PROJECT_DATE_RANGE_INVALID,
					"Project start date must not be after end date");
		}
		return new CanonicalProject(name, description, request.startDate(), endDate, status, position, request.teamSize(),
				normalizeOptional(request.responsibilities()), normalizeOptional(request.programmingLanguages()),
				normalizeOptional(request.tools()));
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
