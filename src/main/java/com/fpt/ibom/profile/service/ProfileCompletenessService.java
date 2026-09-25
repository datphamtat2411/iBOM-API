package com.fpt.ibom.profile.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.master.entity.Language;
import com.fpt.ibom.master.entity.Skill;
import com.fpt.ibom.profile.dto.ProfileCompletenessResponse;
import com.fpt.ibom.profile.dto.ProfileCompletenessSectionResponse;
import com.fpt.ibom.profile.entity.Certificate;
import com.fpt.ibom.profile.entity.Education;
import com.fpt.ibom.profile.entity.EducationStatus;
import com.fpt.ibom.profile.entity.ProfileLanguage;
import com.fpt.ibom.profile.entity.ProfileSkill;
import com.fpt.ibom.profile.entity.Project;
import com.fpt.ibom.profile.entity.ProjectStatus;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.repository.CertificateRepository;
import com.fpt.ibom.profile.repository.EducationRepository;
import com.fpt.ibom.profile.repository.ProfileLanguageRepository;
import com.fpt.ibom.profile.repository.ProfileRepository;
import com.fpt.ibom.profile.repository.ProfileSkillRepository;
import com.fpt.ibom.profile.repository.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileCompletenessService {
	private static final int ABOUT_ME_WEIGHT = 20;
	private static final int ABOUT_ME_FIELD_COUNT = 6;
	private static final int EDUCATION_WEIGHT = 20;
	private static final int LANGUAGE_WEIGHT = 15;
	private static final int CERTIFICATE_WEIGHT = 15;
	private static final int PROJECT_WEIGHT = 20;
	private static final int SKILLS_WEIGHT = 10;

	private final ProfileRepository profileRepository;
	private final EducationRepository educationRepository;
	private final ProfileLanguageRepository profileLanguageRepository;
	private final CertificateRepository certificateRepository;
	private final ProjectRepository projectRepository;
	private final ProfileSkillRepository profileSkillRepository;
	private final Clock clock;

	public ProfileCompletenessService(ProfileRepository profileRepository, EducationRepository educationRepository,
			ProfileLanguageRepository profileLanguageRepository, CertificateRepository certificateRepository,
			ProjectRepository projectRepository, ProfileSkillRepository profileSkillRepository, Clock clock) {
		this.profileRepository = profileRepository;
		this.educationRepository = educationRepository;
		this.profileLanguageRepository = profileLanguageRepository;
		this.certificateRepository = certificateRepository;
		this.projectRepository = projectRepository;
		this.profileSkillRepository = profileSkillRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public ProfileCompletenessResponse get(Long userId, Long profileId) {
		Profile profile = profileRepository.findByIdAndUserIdAndDeletedAtIsNull(profileId, userId)
				.orElseThrow(this::profileNotFound);
		return calculate(profile);
	}

	@Transactional(readOnly = true)
	public ProfileCompletenessResponse calculate(Profile profile) {
		int validAboutMeFields = validAboutMeFields(profile);
		Long profileId = profile.getId();
		boolean hasEducation = hasQualifyingRecord(educationRepository.findByProfileIdOrderByIdAsc(profileId), profileId,
				Education::getProfile, this::isQualifyingEducation);
		boolean hasLanguage = hasQualifyingRecord(profileLanguageRepository.findByProfileId(profileId), profileId,
				ProfileLanguage::getProfile, this::isQualifyingLanguage);
		boolean hasCertificate = hasQualifyingRecord(certificateRepository.findByProfileIdOrderByIssueDateDescIdAsc(profileId),
				profileId, Certificate::getProfile, this::isQualifyingCertificate);
		boolean hasProject = hasQualifyingRecord(projectRepository.findByProfileIdInDisplayOrder(profileId), profileId,
				Project::getProfile, this::isQualifyingProject);
		boolean hasSkill = hasQualifyingRecord(profileSkillRepository.findByProfileId(profileId), profileId,
				ProfileSkill::getProfile, this::isQualifyingSkill);

		int collectionPoints = (hasEducation ? EDUCATION_WEIGHT : 0) + (hasLanguage ? LANGUAGE_WEIGHT : 0)
				+ (hasCertificate ? CERTIFICATE_WEIGHT : 0) + (hasProject ? PROJECT_WEIGHT : 0)
				+ (hasSkill ? SKILLS_WEIGHT : 0);
		long rawTotalNumerator = (long) ABOUT_ME_WEIGHT * validAboutMeFields + 6L * collectionPoints;
		BigDecimal percentage = BigDecimal.valueOf(rawTotalNumerator)
				.divide(BigDecimal.valueOf(ABOUT_ME_FIELD_COUNT), 2, RoundingMode.HALF_UP);
		boolean completed = isCompleted(validAboutMeFields, hasEducation, hasLanguage, hasCertificate, hasProject,
				hasSkill);

		return new ProfileCompletenessResponse(percentage, completed, List.of(
				new ProfileCompletenessSectionResponse("aboutMe", ABOUT_ME_WEIGHT,
						validAboutMeFields == ABOUT_ME_FIELD_COUNT, validAboutMeFields, ABOUT_ME_FIELD_COUNT, null),
				collection("education", EDUCATION_WEIGHT, hasEducation),
				collection("language", LANGUAGE_WEIGHT, hasLanguage),
				collection("certificate", CERTIFICATE_WEIGHT, hasCertificate),
				collection("project", PROJECT_WEIGHT, hasProject),
				collection("skills", SKILLS_WEIGHT, hasSkill)));
	}

	@Transactional(readOnly = true)
	public int countCompleted(List<Profile> profiles) {
		if (profiles.isEmpty()) {
			return 0;
		}

		List<Long> profileIds = profiles.stream().map(Profile::getId).toList();
		Set<Long> educationProfileIds = qualifyingProfileIds(educationRepository.findByProfileIdIn(profileIds),
				Education::getProfile, this::isQualifyingEducation);
		Set<Long> languageProfileIds = qualifyingProfileIds(profileLanguageRepository.findByProfileIdIn(profileIds),
				ProfileLanguage::getProfile, this::isQualifyingLanguage);
		Set<Long> certificateProfileIds = qualifyingProfileIds(certificateRepository.findByProfileIdIn(profileIds),
				Certificate::getProfile, this::isQualifyingCertificate);
		Set<Long> projectProfileIds = qualifyingProfileIds(projectRepository.findByProfileIdIn(profileIds), Project::getProfile,
				this::isQualifyingProject);
		Set<Long> skillProfileIds = qualifyingProfileIds(profileSkillRepository.findByProfileIdIn(profileIds),
				ProfileSkill::getProfile, this::isQualifyingSkill);

		int completedProfiles = 0;
		for (Profile profile : profiles) {
			Long profileId = profile.getId();
			if (isCompleted(validAboutMeFields(profile), educationProfileIds.contains(profileId),
					languageProfileIds.contains(profileId), certificateProfileIds.contains(profileId),
					projectProfileIds.contains(profileId), skillProfileIds.contains(profileId))) {
				completedProfiles++;
			}
		}
		return completedProfiles;
	}

	private ProfileCompletenessSectionResponse collection(String key, int weight, boolean hasQualifyingRecord) {
		return new ProfileCompletenessSectionResponse(key, weight, hasQualifyingRecord, null, null,
				hasQualifyingRecord);
	}

	private <T> boolean hasQualifyingRecord(List<T> records, Long profileId, Function<T, Profile> profileOf,
			Predicate<T> qualifies) {
		return records != null && records.stream().anyMatch(record -> record != null && qualifies.test(record)
				&& belongsToProfile(profileOf.apply(record), profileId));
	}

	private <T> Set<Long> qualifyingProfileIds(List<T> records, Function<T, Profile> profileOf, Predicate<T> qualifies) {
		Set<Long> profileIds = new HashSet<>();
		if (records == null) {
			return profileIds;
		}
		for (T record : records) {
			if (record != null && qualifies.test(record)) {
				Profile profile = profileOf.apply(record);
				if (profile != null && profile.getId() != null) {
					profileIds.add(profile.getId());
				}
			}
		}
		return profileIds;
	}

	private boolean belongsToProfile(Profile profile, Long profileId) {
		return profile != null && profile.getId() != null && profile.getId().equals(profileId);
	}

	private boolean isQualifyingEducation(Education education) {
		if (!isRequiredText(education.getSchoolName(), 255) || !isRequiredText(education.getDegree(), 255)
				|| education.getStartDate() == null || education.getStatus() == null
				|| education.getFieldOfStudy() != null && education.getFieldOfStudy().length() > 255) {
			return false;
		}
		if (education.getStatus() == EducationStatus.ONGOING) {
			return education.getEndDate() == null;
		}
		return education.getStatus() == EducationStatus.COMPLETED && education.getEndDate() != null
				&& !education.getStartDate().isAfter(education.getEndDate());
	}

	private boolean isQualifyingLanguage(ProfileLanguage profileLanguage) {
		Language language = profileLanguage.getLanguage();
		return language != null && hasPositiveId(language.getId()) && profileLanguage.getLevel() != null;
	}

	private boolean isQualifyingCertificate(Certificate certificate) {
		return isRequiredText(certificate.getCertificateName(), 255) && certificate.getIssueDate() != null
				&& !certificate.getIssueDate().isAfter(LocalDate.now(clock));
	}

	private boolean isQualifyingProject(Project project) {
		if (!isRequiredText(project.getName(), 255) || !isNonBlank(project.getDescription())
				|| !isRequiredText(project.getPosition(), 255) || project.getStatus() == null
				|| project.getTeamSize() != null && project.getTeamSize() < 1) {
			return false;
		}
		if (project.getStatus() == ProjectStatus.ONGOING) {
			return project.getEndDate() == null;
		}
		return project.getStatus() == ProjectStatus.COMPLETED && project.getEndDate() != null
				&& (project.getStartDate() == null || !project.getStartDate().isAfter(project.getEndDate()));
	}

	private boolean isQualifyingSkill(ProfileSkill profileSkill) {
		Skill skill = profileSkill.getSkill();
		return skill != null && hasPositiveId(skill.getId()) && profileSkill.getExperienceYears() != null
				&& profileSkill.getExperienceYears().signum() >= 0
				&& (profileSkill.getLastUsed() == null
						|| !profileSkill.getLastUsed().isAfter(LocalDate.now(clock)));
	}

	private boolean hasPositiveId(Long id) {
		return id != null && id > 0;
	}

	private boolean isRequiredText(String value, int maxLength) {
		return isNonBlank(value) && value.trim().length() <= maxLength;
	}

	private boolean isCompleted(int validAboutMeFields, boolean hasEducation, boolean hasLanguage,
			boolean hasCertificate, boolean hasProject, boolean hasSkill) {
		return validAboutMeFields == ABOUT_ME_FIELD_COUNT && hasEducation && hasLanguage && hasCertificate && hasProject
				&& hasSkill;
	}

	private int validAboutMeFields(Profile profile) {
		int count = 0;
		if (isNonBlank(profile.getFirstName())) {
			count++;
		}
		if (isNonBlank(profile.getLastName())) {
			count++;
		}
		if (isNonBlank(profile.getJobTitle())) {
			count++;
		}
		if (profile.getYearsOfExperience() != null && profile.getYearsOfExperience().signum() >= 0) {
			count++;
		}
		if (isNonBlank(profile.getPersonality())) {
			count++;
		}
		if (isNonBlank(profile.getTechnicalSummary())) {
			count++;
		}
		return count;
	}

	private boolean isNonBlank(String value) {
		return value != null && !value.isBlank();
	}

	private ApiException profileNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.PROFILE_NOT_FOUND, "Profile not found");
	}
}
