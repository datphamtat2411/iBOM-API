package com.fpt.ibom.cv.service;

import com.fpt.ibom.cv.model.CvCertificate;
import com.fpt.ibom.cv.model.CvDocument;
import com.fpt.ibom.cv.model.CvEducation;
import com.fpt.ibom.cv.model.CvEducationStatus;
import com.fpt.ibom.cv.model.CvLanguage;
import com.fpt.ibom.cv.model.CvLanguageLevel;
import com.fpt.ibom.cv.model.CvPersonalDetails;
import com.fpt.ibom.cv.model.CvProject;
import com.fpt.ibom.cv.model.CvProjectStatus;
import com.fpt.ibom.cv.model.CvSkill;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.profile.entity.Education;
import com.fpt.ibom.profile.entity.EducationStatus;
import com.fpt.ibom.profile.entity.LanguageLevel;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.entity.ProfileLanguage;
import com.fpt.ibom.profile.entity.ProfileSkill;
import com.fpt.ibom.profile.entity.Project;
import com.fpt.ibom.profile.entity.ProjectStatus;
import com.fpt.ibom.profile.entity.Certificate;
import com.fpt.ibom.profile.repository.CertificateRepository;
import com.fpt.ibom.profile.repository.EducationRepository;
import com.fpt.ibom.profile.repository.ProfileLanguageRepository;
import com.fpt.ibom.profile.repository.ProfileRepository;
import com.fpt.ibom.profile.repository.ProfileSkillRepository;
import com.fpt.ibom.profile.repository.ProjectRepository;
import com.fpt.ibom.profile.service.ProfileDisplayOrder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CvDocumentAssembler {

	private final ProfileRepository profileRepository;
	private final EducationRepository educationRepository;
	private final ProfileLanguageRepository profileLanguageRepository;
	private final CertificateRepository certificateRepository;
	private final ProjectRepository projectRepository;
	private final ProfileSkillRepository profileSkillRepository;

	public CvDocumentAssembler(ProfileRepository profileRepository, EducationRepository educationRepository,
			ProfileLanguageRepository profileLanguageRepository, CertificateRepository certificateRepository,
			ProjectRepository projectRepository, ProfileSkillRepository profileSkillRepository) {
		this.profileRepository = profileRepository;
		this.educationRepository = educationRepository;
		this.profileLanguageRepository = profileLanguageRepository;
		this.certificateRepository = certificateRepository;
		this.projectRepository = projectRepository;
		this.profileSkillRepository = profileSkillRepository;
	}

	@Transactional(readOnly = true)
	public CvDocument assemble(Long profileId) {
		Profile profile = profileRepository.findByIdAndDeletedAtIsNull(profileId).orElseThrow(this::profileNotFound);
		return new CvDocument(toPersonalDetails(profile),
				educationRepository.findByProfileIdOrderByIdAsc(profileId).stream().map(this::toEducation).toList(),
				profileLanguageRepository.findByProfileId(profileId).stream().sorted(ProfileDisplayOrder.languageComparator())
						.map(this::toLanguage).toList(),
				certificateRepository.findByProfileIdOrderByIssueDateDescIdAsc(profileId).stream().map(this::toCertificate)
						.toList(),
				projectRepository.findByProfileIdInDisplayOrder(profileId).stream().map(this::toProject).toList(),
				profileSkillRepository.findByProfileId(profileId).stream().sorted(ProfileDisplayOrder.skillComparator())
						.map(this::toSkill).toList());
	}

	private CvPersonalDetails toPersonalDetails(Profile profile) {
		return new CvPersonalDetails(profile.getFirstName(), profile.getLastName(), profile.getJobTitle(),
				profile.getYearsOfExperience(), profile.getPersonality(), profile.getTechnicalSummary());
	}

	private CvEducation toEducation(Education education) {
		return new CvEducation(education.getSchoolName(), education.getDegree(), education.getFieldOfStudy(),
				education.getStartDate(), education.getEndDate(), toEducationStatus(education.getStatus()));
	}

	private CvLanguage toLanguage(ProfileLanguage profileLanguage) {
		return new CvLanguage(profileLanguage.getLanguage().getName(), toLanguageLevel(profileLanguage.getLevel()));
	}

	private CvCertificate toCertificate(Certificate certificate) {
		return new CvCertificate(certificate.getCertificateName(), certificate.getIssueDate());
	}

	private CvProject toProject(Project project) {
		return new CvProject(project.getName(), project.getDescription(), project.getStartDate(), project.getEndDate(),
				toProjectStatus(project.getStatus()), project.getPosition(), project.getTeamSize(), project.getResponsibilities(),
				project.getProgrammingLanguages(), project.getTools());
	}

	private CvSkill toSkill(ProfileSkill profileSkill) {
		return new CvSkill(profileSkill.getSkill().getName(), profileSkill.getSkill().getCategory().getCode(),
				profileSkill.getSkill().getCategory().getName(), profileSkill.getExperienceYears(), profileSkill.getLastUsed());
	}

	private CvEducationStatus toEducationStatus(EducationStatus status) {
		return switch (status) {
		case ONGOING -> CvEducationStatus.ONGOING;
		case COMPLETED -> CvEducationStatus.COMPLETED;
		};
	}

	private CvLanguageLevel toLanguageLevel(LanguageLevel level) {
		return switch (level) {
		case BEGINNER -> CvLanguageLevel.BEGINNER;
		case INTERMEDIATE -> CvLanguageLevel.INTERMEDIATE;
		case UPPER_INTERMEDIATE -> CvLanguageLevel.UPPER_INTERMEDIATE;
		case ADVANCED -> CvLanguageLevel.ADVANCED;
		case NATIVE -> CvLanguageLevel.NATIVE;
		};
	}

	private CvProjectStatus toProjectStatus(ProjectStatus status) {
		return switch (status) {
		case ONGOING -> CvProjectStatus.ONGOING;
		case COMPLETED -> CvProjectStatus.COMPLETED;
		};
	}

	private ApiException profileNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.PROFILE_NOT_FOUND, "Profile not found");
	}
}
