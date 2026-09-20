package com.fpt.ibom.profile.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.profile.dto.ProfileCompletenessResponse;
import com.fpt.ibom.profile.dto.ProfileCompletenessSectionResponse;
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

	public ProfileCompletenessService(ProfileRepository profileRepository, EducationRepository educationRepository,
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
	public ProfileCompletenessResponse get(Long userId, Long profileId) {
		Profile profile = profileRepository.findByIdAndUserIdAndDeletedAtIsNull(profileId, userId)
				.orElseThrow(this::profileNotFound);
		return calculate(profile);
	}

	@Transactional(readOnly = true)
	public ProfileCompletenessResponse calculate(Profile profile) {
		int validAboutMeFields = validAboutMeFields(profile);
		boolean hasEducation = educationRepository.existsByProfileId(profile.getId());
		boolean hasLanguage = profileLanguageRepository.existsByProfileId(profile.getId());
		boolean hasCertificate = certificateRepository.existsByProfileId(profile.getId());
		boolean hasProject = projectRepository.existsByProfileId(profile.getId());
		boolean hasSkill = profileSkillRepository.existsByProfileId(profile.getId());

		int collectionPoints = (hasEducation ? EDUCATION_WEIGHT : 0) + (hasLanguage ? LANGUAGE_WEIGHT : 0)
				+ (hasCertificate ? CERTIFICATE_WEIGHT : 0) + (hasProject ? PROJECT_WEIGHT : 0)
				+ (hasSkill ? SKILLS_WEIGHT : 0);
		long rawTotalNumerator = (long) ABOUT_ME_WEIGHT * validAboutMeFields + 6L * collectionPoints;
		int percentage = BigDecimal.valueOf(rawTotalNumerator)
				.divide(BigDecimal.valueOf(ABOUT_ME_FIELD_COUNT), 0, RoundingMode.HALF_UP).intValueExact();
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
		Set<Long> educationProfileIds = new HashSet<>(educationRepository.findProfileIdsByProfileIdIn(profileIds));
		Set<Long> languageProfileIds = new HashSet<>(profileLanguageRepository.findProfileIdsByProfileIdIn(profileIds));
		Set<Long> certificateProfileIds = new HashSet<>(certificateRepository.findProfileIdsByProfileIdIn(profileIds));
		Set<Long> projectProfileIds = new HashSet<>(projectRepository.findProfileIdsByProfileIdIn(profileIds));
		Set<Long> skillProfileIds = new HashSet<>(profileSkillRepository.findProfileIdsByProfileIdIn(profileIds));

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
