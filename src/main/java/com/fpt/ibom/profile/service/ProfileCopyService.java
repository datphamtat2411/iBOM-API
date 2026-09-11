package com.fpt.ibom.profile.service;

import java.util.List;

import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.profile.dto.ProfileCopyRequest;
import com.fpt.ibom.profile.dto.ProfileResponse;
import com.fpt.ibom.profile.entity.Certificate;
import com.fpt.ibom.profile.entity.Education;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.entity.ProfileLanguage;
import com.fpt.ibom.profile.entity.ProfileSkill;
import com.fpt.ibom.profile.entity.Project;
import com.fpt.ibom.profile.repository.CertificateRepository;
import com.fpt.ibom.profile.repository.EducationRepository;
import com.fpt.ibom.profile.repository.ProfileLanguageRepository;
import com.fpt.ibom.profile.repository.ProfileRepository;
import com.fpt.ibom.profile.repository.ProfileSkillRepository;
import com.fpt.ibom.profile.repository.ProjectRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileCopyService {
	private static final String ACTIVE_PROFILE_NAME_CONSTRAINT = "uk_profiles_user_active_name";

	private final ProfileRepository profileRepository;
	private final EducationRepository educationRepository;
	private final ProfileLanguageRepository profileLanguageRepository;
	private final CertificateRepository certificateRepository;
	private final ProjectRepository projectRepository;
	private final ProfileSkillRepository profileSkillRepository;

	public ProfileCopyService(ProfileRepository profileRepository, EducationRepository educationRepository,
			ProfileLanguageRepository profileLanguageRepository, CertificateRepository certificateRepository,
			ProjectRepository projectRepository, ProfileSkillRepository profileSkillRepository) {
		this.profileRepository = profileRepository;
		this.educationRepository = educationRepository;
		this.profileLanguageRepository = profileLanguageRepository;
		this.certificateRepository = certificateRepository;
		this.projectRepository = projectRepository;
		this.profileSkillRepository = profileSkillRepository;
	}

	@Transactional
	public ProfileResponse copy(Long userId, Long sourceProfileId, ProfileCopyRequest request) {
		Profile source = profileRepository.findByIdAndUserIdAndDeletedAtIsNull(sourceProfileId, userId)
				.orElseThrow(this::profileNotFound);
		String profileName = request.profileName().trim();
		if (profileRepository.existsByUserIdAndDeletedAtIsNullAndProfileNameIgnoreCase(userId, profileName)) {
			throw duplicateName();
		}

		Profile copy = new Profile(source.getUser(), profileName, source.getFirstName(), source.getLastName(),
				source.getJobTitle(), source.getYearsOfExperience(), source.getPersonality(), source.getTechnicalSummary());
		try {
			profileRepository.saveAndFlush(copy);
		} catch (DataIntegrityViolationException exception) {
			if (isActiveProfileNameConstraintViolation(exception)) {
				throw duplicateName();
			}
			throw exception;
		}

		List<Education> educations = educationRepository.findByProfileIdOrderByIdAsc(sourceProfileId).stream()
				.map(education -> new Education(copy, education.getSchoolName(), education.getDegree(),
						education.getFieldOfStudy(), education.getStartDate(), education.getEndDate(), education.getStatus()))
				.toList();
		List<ProfileLanguage> profileLanguages = profileLanguageRepository.findByProfileId(sourceProfileId).stream()
				.map(profileLanguage -> new ProfileLanguage(copy, profileLanguage.getLanguage(), profileLanguage.getLevel()))
				.toList();
		List<Certificate> certificates = certificateRepository.findByProfileIdOrderByIssueDateDescIdAsc(sourceProfileId)
				.stream().map(certificate -> new Certificate(copy, certificate.getCertificateName(), certificate.getIssueDate()))
				.toList();
		List<Project> projects = projectRepository.findByProfileIdInDisplayOrder(sourceProfileId).stream()
				.map(project -> new Project(copy, project.getName(), project.getDescription(), project.getStartDate(),
						project.getEndDate(), project.getStatus(), project.getPosition(), project.getTeamSize(),
						project.getResponsibilities(), project.getProgrammingLanguages(), project.getTools()))
				.toList();
		List<ProfileSkill> profileSkills = profileSkillRepository.findByProfileId(sourceProfileId).stream()
				.map(profileSkill -> new ProfileSkill(copy, profileSkill.getSkill(), profileSkill.getExperienceYears(),
						profileSkill.getLastUsed()))
				.toList();

		educationRepository.saveAll(educations);
		profileLanguageRepository.saveAll(profileLanguages);
		certificateRepository.saveAll(certificates);
		projectRepository.saveAll(projects);
		profileSkillRepository.saveAll(profileSkills);

		return ProfileResponse.from(copy);
	}

	private ApiException duplicateName() {
		return new ApiException(HttpStatus.CONFLICT, ErrorCode.PROFILE_NAME_ALREADY_EXISTS,
				"Profile name is already in use");
	}

	private boolean isActiveProfileNameConstraintViolation(DataIntegrityViolationException exception) {
		Throwable cause = exception;
		while (cause != null) {
			if (cause instanceof ConstraintViolationException constraintViolation) {
				return ACTIVE_PROFILE_NAME_CONSTRAINT.equals(constraintViolation.getConstraintName());
			}
			cause = cause.getCause();
		}
		return false;
	}

	private ApiException profileNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.PROFILE_NOT_FOUND, "Profile not found");
	}
}
