package com.fpt.ibom.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fpt.ibom.MySqlIntegrationTest;
import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.master.entity.Language;
import com.fpt.ibom.master.entity.FileNameFormat;
import com.fpt.ibom.master.entity.Skill;
import com.fpt.ibom.master.entity.SkillCategory;
import com.fpt.ibom.master.repository.LanguageRepository;
import com.fpt.ibom.master.repository.FileNameFormatRepository;
import com.fpt.ibom.master.repository.SkillCategoryRepository;
import com.fpt.ibom.master.repository.SkillRepository;
import com.fpt.ibom.profile.dto.ProfileCopyRequest;
import com.fpt.ibom.profile.dto.EducationRequest;
import com.fpt.ibom.profile.dto.ProfileDetailResponse;
import com.fpt.ibom.profile.dto.ProfileLanguageRequest;
import com.fpt.ibom.profile.dto.ProfileUpdateRequest;
import com.fpt.ibom.profile.dto.ProfileResponse;
import com.fpt.ibom.profile.entity.Certificate;
import com.fpt.ibom.profile.entity.Education;
import com.fpt.ibom.profile.entity.EducationStatus;
import com.fpt.ibom.profile.entity.LanguageLevel;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.entity.ProfileLanguage;
import com.fpt.ibom.profile.entity.ProfileSkill;
import com.fpt.ibom.profile.entity.Project;
import com.fpt.ibom.profile.entity.ProjectStatus;
import com.fpt.ibom.profile.repository.CertificateRepository;
import com.fpt.ibom.profile.repository.EducationRepository;
import com.fpt.ibom.profile.repository.ProfileLanguageRepository;
import com.fpt.ibom.profile.repository.ProfileRepository;
import com.fpt.ibom.profile.repository.ProfileSkillRepository;
import com.fpt.ibom.profile.repository.ProjectRepository;
import com.fpt.ibom.profile.service.ProfileCopyService;
import com.fpt.ibom.profile.service.CertificateService;
import com.fpt.ibom.profile.service.EducationService;
import com.fpt.ibom.profile.service.ProfileLanguageService;
import com.fpt.ibom.profile.service.ProfileService;
import com.fpt.ibom.profile.service.ProjectService;
import com.fpt.ibom.profile.service.ProfileSkillService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@AutoConfigureMockMvc
class ProfileCopyIntegrationTest extends MySqlIntegrationTest {
	@Autowired
	private ProfileCopyService profileCopyService;
	@Autowired
	private ProfileService profileService;
	@Autowired
	private EducationService educationService;
	@Autowired
	private CertificateService certificateService;
	@Autowired
	private ProjectService projectService;
	@Autowired
	private ProfileLanguageService profileLanguageService;
	@Autowired
	private ProfileSkillService profileSkillService;
	@Autowired
	private ProfileRepository profileRepository;
	@Autowired
	private EducationRepository educationRepository;
	@Autowired
	private ProfileLanguageRepository profileLanguageRepository;
	@Autowired
	private CertificateRepository certificateRepository;
	@Autowired
	private ProjectRepository projectRepository;
	@Autowired
	private ProfileSkillRepository profileSkillRepository;
	@Autowired
	private LanguageRepository languageRepository;
	@Autowired
	private FileNameFormatRepository fileNameFormatRepository;
	@Autowired
	private SkillRepository skillRepository;
	@Autowired
	private SkillCategoryRepository skillCategoryRepository;
	@Autowired
	private UserAccountRepository userRepository;

	@Test
	void persistsIndependentFullAggregateWithFreshStateAndSharedMasters() {
		UserAccount user = saveUser();
		String sourceName = "Source-" + UUID.randomUUID();
		Profile source = saveProfile(user, sourceName);
		FileNameFormat preferredFormat = fileNameFormatRepository
				.saveAndFlush(new FileNameFormat("Copy Format-" + UUID.randomUUID(), "{LastName}_{Date}", false));
		source.setPreferredFileNameFormat(preferredFormat);
		source.markExportedAt(Instant.parse("2026-02-03T04:05:06Z"));
		source = profileRepository.saveAndFlush(source);
		source.update(sourceName, "First", "Last", "Engineer", new BigDecimal("3.5"), "Personality", "Summary");
		source = profileRepository.saveAndFlush(source);
		source.softDelete(null);
		org.springframework.test.util.ReflectionTestUtils.setField(source, "hasPreviewed", true);
		source = profileRepository.saveAndFlush(source);

		Language language = languageRepository.saveAndFlush(new Language("copy-language-" + UUID.randomUUID()));
		SkillCategory category = skillCategoryRepository.findByCode("BACKEND").orElseThrow();
		Skill skill = skillRepository.saveAndFlush(new Skill("copy-skill-" + UUID.randomUUID(), category));
		Education sourceEducation = educationRepository.saveAndFlush(new Education(source, "School", "Degree", "Field",
				LocalDate.of(2020, 1, 1), LocalDate.of(2024, 1, 1), EducationStatus.COMPLETED));
		ProfileLanguage sourceLanguage = profileLanguageRepository
				.saveAndFlush(new ProfileLanguage(source, language, LanguageLevel.NATIVE));
		Certificate sourceCertificate = certificateRepository
				.saveAndFlush(new Certificate(source, "Certificate", LocalDate.of(2023, 5, 1)));
		Project sourceProject = projectRepository.saveAndFlush(new Project(source, "Project", "Description",
				LocalDate.of(2022, 1, 1), LocalDate.of(2023, 1, 1), ProjectStatus.COMPLETED, "Engineer", 4,
				"Responsibilities", "Java", "Tools"));
		ProfileSkill sourceSkill = profileSkillRepository
				.saveAndFlush(new ProfileSkill(source, skill, new BigDecimal("2.5"), LocalDate.of(2025, 1, 1)));
		long languageCount = languageRepository.count();
		long skillCount = skillRepository.count();

		String copyName = "Copy-" + UUID.randomUUID();
		ProfileResponse response = profileCopyService.copy(user.getId(), source.getId(), new ProfileCopyRequest(" " + copyName + " "));

		assertNotNull(response.id());
		assertEquals(user.getId(), response.userId());
		assertEquals(copyName, response.profileName());
		assertEquals("First", response.firstName());
		assertEquals("Last", response.lastName());
		assertEquals("Engineer", response.jobTitle());
		assertEquals(new BigDecimal("3.50"), response.yearsOfExperience());
		assertEquals("Personality", response.personality());
		assertEquals("Summary", response.technicalSummary());
		assertFalse(response.hasPreviewed());
		assertNull(response.lastExportedAt());
		assertNull(response.preferredFileNameFormatId());
		assertEquals(0L, response.version());
		assertNotNull(response.createdAt());
		assertNotNull(response.updatedAt());

		Profile copied = profileRepository.findById(response.id()).orElseThrow();
		assertNotEquals(source.getId(), copied.getId());
		assertFalse(copied.isHasPreviewed());
		assertNull(copied.getLastExportedAt());
		assertNull(copied.getPreferredFileNameFormat());
		assertEquals(0L, copied.getVersion());
		assertNull(copied.getDeletedAt());
		assertNotNull(copied.getCreatedAt());
		assertNotNull(copied.getUpdatedAt());
		assertNotEquals(source.getCreatedAt(), copied.getCreatedAt());

		Education copiedEducation = educationRepository.findByProfileIdOrderByIdAsc(copied.getId()).get(0);
		assertNotEquals(sourceEducation.getId(), copiedEducation.getId());
		assertEquals(copied.getId(), copiedEducation.getProfile().getId());
		assertEquals(sourceEducation.getSchoolName(), copiedEducation.getSchoolName());
		assertEquals(sourceEducation.getDegree(), copiedEducation.getDegree());
		assertNotNull(copiedEducation.getCreatedAt());
		assertNotNull(copiedEducation.getUpdatedAt());
		assertNotEquals(sourceEducation.getCreatedAt(), copiedEducation.getCreatedAt());

		ProfileLanguage copiedLanguage = profileLanguageRepository.findByProfileId(copied.getId()).get(0);
		assertNotEquals(sourceLanguage.getId(), copiedLanguage.getId());
		assertEquals(copied.getId(), copiedLanguage.getProfile().getId());
		assertEquals(language.getId(), copiedLanguage.getLanguage().getId());
		assertEquals(LanguageLevel.NATIVE, copiedLanguage.getLevel());
		assertNotNull(copiedLanguage.getCreatedAt());
		assertNotNull(copiedLanguage.getUpdatedAt());

		Certificate copiedCertificate = certificateRepository.findByProfileIdOrderByIssueDateDescIdAsc(copied.getId())
				.get(0);
		assertNotEquals(sourceCertificate.getId(), copiedCertificate.getId());
		assertEquals(copied.getId(), copiedCertificate.getProfile().getId());
		assertEquals(sourceCertificate.getCertificateName(), copiedCertificate.getCertificateName());
		assertNotNull(copiedCertificate.getCreatedAt());
		assertNotNull(copiedCertificate.getUpdatedAt());

		Project copiedProject = projectRepository.findByProfileIdInDisplayOrder(copied.getId()).get(0);
		assertNotEquals(sourceProject.getId(), copiedProject.getId());
		assertEquals(copied.getId(), copiedProject.getProfile().getId());
		assertEquals(sourceProject.getResponsibilities(), copiedProject.getResponsibilities());
		assertNotNull(copiedProject.getCreatedAt());
		assertNotNull(copiedProject.getUpdatedAt());

		ProfileSkill copiedSkill = profileSkillRepository.findByProfileId(copied.getId()).get(0);
		assertNotEquals(sourceSkill.getId(), copiedSkill.getId());
		assertEquals(copied.getId(), copiedSkill.getProfile().getId());
		assertEquals(skill.getId(), copiedSkill.getSkill().getId());
		assertEquals(0, sourceSkill.getExperienceYears().compareTo(copiedSkill.getExperienceYears()));

		assertEquals(languageCount, languageRepository.count());
		assertEquals(skillCount, skillRepository.count());
		assertEquals(language.getName(), copiedLanguage.getLanguage().getName());
		assertEquals(skill.getName(), copiedSkill.getSkill().getName());

		copied.update("Changed Copy", "Changed First", "Changed Last", "Changed Job", BigDecimal.ZERO, "Changed",
				"Changed summary");
		profileRepository.saveAndFlush(copied);
		copiedEducation.update("Changed School", "Changed Degree", "Changed Field", LocalDate.of(2021, 1, 1), null,
				EducationStatus.ONGOING);
		educationRepository.saveAndFlush(copiedEducation);
		copiedLanguage.update(language, LanguageLevel.ADVANCED);
		profileLanguageRepository.saveAndFlush(copiedLanguage);
		copiedCertificate.update("Changed Certificate", LocalDate.of(2024, 1, 1));
		certificateRepository.saveAndFlush(copiedCertificate);
		copiedProject.update("Changed Project", "Changed Description", LocalDate.of(2023, 1, 1), null,
				ProjectStatus.ONGOING, "Changed Position", 2, "Changed Responsibilities", "Kotlin", "Changed Tools");
		projectRepository.saveAndFlush(copiedProject);
		copiedSkill.update(skill, BigDecimal.ONE, null);
		profileSkillRepository.saveAndFlush(copiedSkill);

		Profile unchangedSource = profileRepository.findById(source.getId()).orElseThrow();
		assertEquals(sourceName, unchangedSource.getProfileName());
		assertEquals("First", unchangedSource.getFirstName());
		assertEquals("School", educationRepository.findByProfileIdOrderByIdAsc(source.getId()).get(0).getSchoolName());
		assertEquals(LanguageLevel.NATIVE, profileLanguageRepository.findByProfileId(source.getId()).get(0).getLevel());
		assertEquals("Certificate", certificateRepository.findByProfileIdOrderByIssueDateDescIdAsc(source.getId()).get(0)
				.getCertificateName());
		assertEquals("Responsibilities", projectRepository.findByProfileIdInDisplayOrder(source.getId()).get(0)
				.getResponsibilities());
		assertEquals(new BigDecimal("2.50"), profileSkillRepository.findByProfileId(source.getId()).get(0)
				.getExperienceYears());
}

	@Test
	void copiesEmptySectionsAndRejectsForeignOrSoftDeletedSources() {
		UserAccount owner = saveUser();
		UserAccount foreignUser = saveUser();
		Profile source = saveProfile(owner, "Empty Source-" + UUID.randomUUID());
		Profile foreign = saveProfile(foreignUser, "Foreign Source-" + UUID.randomUUID());

		assertThrowsWithCode(() -> profileCopyService.copy(owner.getId(), foreign.getId(),
				new ProfileCopyRequest("Foreign Copy")), ErrorCode.PROFILE_NOT_FOUND);

		Profile deleted = profileRepository.findById(source.getId()).orElseThrow();
		deleted.softDelete(Instant.now());
		profileRepository.saveAndFlush(deleted);
		assertThrowsWithCode(() -> profileCopyService.copy(owner.getId(), source.getId(),
				new ProfileCopyRequest("Deleted Copy")), ErrorCode.PROFILE_NOT_FOUND);

		Profile emptySource = saveProfile(owner, "Active Empty-" + UUID.randomUUID());
		ProfileResponse response = profileCopyService.copy(owner.getId(), emptySource.getId(),
				new ProfileCopyRequest("Empty Copy-" + UUID.randomUUID()));
		assertNotEquals(emptySource.getId(), response.id());
		assertEquals(List.of(), educationRepository.findByProfileIdOrderByIdAsc(response.id()));
		assertEquals(List.of(), profileLanguageRepository.findByProfileId(response.id()));
		assertEquals(List.of(), certificateRepository.findByProfileIdOrderByIssueDateDescIdAsc(response.id()));
		assertEquals(List.of(), projectRepository.findByProfileIdInDisplayOrder(response.id()));
		assertEquals(List.of(), profileSkillRepository.findByProfileId(response.id()));
	}

	@Test
	void maintainsServiceLevelIsolationAcrossNormalCopyMutations() {
		UserAccount user = saveUser();
		CopiedAggregate aggregate = savePopulatedAggregate(user);
		Profile source = aggregate.source();
		Profile copied = aggregate.copied();

		Profile copiedEntity = profileRepository.findById(copied.getId()).orElseThrow();
		ReflectionTestUtils.setField(copiedEntity, "hasPreviewed", true);
		profileRepository.saveAndFlush(copiedEntity);

		ProfileDetailResponse sourceBefore = profileService.get(user.getId(), source.getId());
		ProfileDetailResponse copiedBefore = profileService.get(user.getId(), copied.getId());
		assertTrue(sourceBefore.hasPreviewed());
		assertTrue(copiedBefore.hasPreviewed());
		assertEquals(1, educationService.list(user.getId(), copied.getId()).size());
		assertEquals(1, certificateService.list(user.getId(), copied.getId()).size());
		assertEquals(1, projectService.list(user.getId(), copied.getId()).size());
		assertEquals(1, profileLanguageService.list(user.getId(), copied.getId()).size());
		assertEquals(1, profileSkillService.list(user.getId(), copied.getId()).size());

		ProfileDetailResponse copiedAfterProfileUpdate = profileService.update(user.getId(), copied.getId(),
				new ProfileUpdateRequest("Updated Copy", "Copy First", "Copy Last", "Developer", new BigDecimal("4.0"),
						"Copy Personality", "Copy Summary", copiedBefore.version()));
		assertEquals(1L, copiedAfterProfileUpdate.version());
		assertFalse(copiedAfterProfileUpdate.hasPreviewed());

		Education copiedEducation = educationRepository.findByProfileIdOrderByIdAsc(copied.getId()).get(0);
		educationService.update(user.getId(), copied.getId(), copiedEducation.getId(),
				new EducationRequest("Updated Copy School", "Degree", "Field", LocalDate.of(2021, 1, 1), null,
						"ONGOING", copiedAfterProfileUpdate.version()));
		long copiedVersionAfterEducation = profileService.get(user.getId(), copied.getId()).version();
		assertTrue(copiedVersionAfterEducation > copiedAfterProfileUpdate.version());

		ProfileLanguage copiedLanguage = profileLanguageRepository.findByProfileId(copied.getId()).get(0);
		profileLanguageService.update(user.getId(), copied.getId(), copiedLanguage.getId(),
				new ProfileLanguageRequest(aggregate.language().getId(), "ADVANCED", copiedVersionAfterEducation));
		long copiedVersionAfterLanguage = profileService.get(user.getId(), copied.getId()).version();
		assertTrue(copiedVersionAfterLanguage > copiedVersionAfterEducation);

		ProfileDetailResponse sourceAfterProfileUpdate = profileService.update(user.getId(), source.getId(),
				new ProfileUpdateRequest("Updated Source", "Source First", "Source Last", "Architect",
						new BigDecimal("5.0"), "Source Personality", "Source Summary", sourceBefore.version()));
		Education sourceEducation = educationRepository.findByProfileIdOrderByIdAsc(source.getId()).get(0);
		educationService.update(user.getId(), source.getId(), sourceEducation.getId(),
				new EducationRequest("Updated Source School", "Degree", "Field", LocalDate.of(2021, 1, 1),
						LocalDate.of(2025, 1, 1), "COMPLETED", sourceAfterProfileUpdate.version()));

		ProfileDetailResponse copiedAfterMutations = profileService.get(user.getId(), copied.getId());
		ProfileDetailResponse sourceAfterMutations = profileService.get(user.getId(), source.getId());
		assertEquals("Updated Copy", copiedAfterMutations.profileName());
		assertEquals(copiedVersionAfterLanguage, copiedAfterMutations.version());
		assertFalse(copiedAfterMutations.hasPreviewed());
		assertEquals("Updated Source", sourceAfterMutations.profileName());
		assertTrue(sourceAfterMutations.version() > sourceAfterProfileUpdate.version());
		assertFalse(sourceAfterMutations.hasPreviewed());
		assertEquals("Updated Copy School", educationService.list(user.getId(), copied.getId()).get(0).schoolName());
		assertEquals("Updated Source School", educationService.list(user.getId(), source.getId()).get(0).schoolName());
		assertEquals(LanguageLevel.ADVANCED, profileLanguageService.list(user.getId(), copied.getId()).get(0).level());
		assertEquals(LanguageLevel.NATIVE, profileLanguageService.list(user.getId(), source.getId()).get(0).level());

		ProfileLanguage sourceLanguage = profileLanguageRepository.findByProfileId(source.getId()).get(0);
		ProfileSkill copiedSkill = profileSkillRepository.findByProfileId(copied.getId()).get(0);
		ProfileSkill sourceSkill = profileSkillRepository.findByProfileId(source.getId()).get(0);
		assertNotEquals(sourceLanguage.getId(), copiedLanguage.getId());
		assertEquals(aggregate.language().getId(), sourceLanguage.getLanguage().getId());
		assertEquals(aggregate.language().getId(), copiedLanguage.getLanguage().getId());
		assertNotEquals(sourceSkill.getId(), copiedSkill.getId());
		assertEquals(aggregate.skill().getId(), sourceSkill.getSkill().getId());
		assertEquals(aggregate.skill().getId(), copiedSkill.getSkill().getId());
	}

	@Test
	void keepsCopiedAggregateActiveAfterSourceSoftDeletion() {
		UserAccount user = saveUser();
		CopiedAggregate aggregate = savePopulatedAggregate(user);
		Profile source = aggregate.source();
		Profile copied = aggregate.copied();

		profileService.delete(user.getId(), source.getId());

		assertThrowsWithCode(() -> profileService.get(user.getId(), source.getId()), ErrorCode.PROFILE_NOT_FOUND);
		assertThrowsWithCode(() -> certificateService.list(user.getId(), source.getId()), ErrorCode.PROFILE_NOT_FOUND);
		assertEquals(List.of(copied.getId()), profileService.list(user.getId()).stream().map(response -> response.id()).toList());

		ProfileDetailResponse copiedBeforeUpdate = profileService.get(user.getId(), copied.getId());
		assertEquals(0L, copiedBeforeUpdate.version());
		assertFalse(copiedBeforeUpdate.hasPreviewed());
		assertEquals(1, educationService.list(user.getId(), copied.getId()).size());
		assertEquals(1, certificateService.list(user.getId(), copied.getId()).size());
		assertEquals(1, projectService.list(user.getId(), copied.getId()).size());
		assertEquals(1, profileLanguageService.list(user.getId(), copied.getId()).size());
		assertEquals(1, profileSkillService.list(user.getId(), copied.getId()).size());

		ProfileDetailResponse copiedAfterProfileUpdate = profileService.update(user.getId(), copied.getId(),
				new ProfileUpdateRequest("After Source Delete", "Copy First", "Copy Last", "Developer",
						new BigDecimal("4.0"), "Copy Personality", "Copy Summary", copiedBeforeUpdate.version()));
		Education copiedEducation = educationRepository.findByProfileIdOrderByIdAsc(copied.getId()).get(0);
		educationService.update(user.getId(), copied.getId(), copiedEducation.getId(),
				new EducationRequest("After Delete School", "Degree", "Field", LocalDate.of(2021, 1, 1), null,
						"ONGOING", copiedAfterProfileUpdate.version()));
		ProfileDetailResponse copiedAfterEducationUpdate = profileService.get(user.getId(), copied.getId());
		assertTrue(copiedAfterEducationUpdate.version() > copiedAfterProfileUpdate.version());

		Profile copiedAfterUpdate = profileRepository.findById(copied.getId()).orElseThrow();
		Profile deletedSource = profileRepository.findById(source.getId()).orElseThrow();
		assertEquals(copiedAfterEducationUpdate.version(), copiedAfterUpdate.getVersion());
		assertFalse(copiedAfterUpdate.isHasPreviewed());
		assertNotNull(deletedSource.getDeletedAt());
		assertEquals(1, educationRepository.findByProfileIdOrderByIdAsc(copied.getId()).size());
		assertEquals(1, profileLanguageRepository.findByProfileId(copied.getId()).size());
		assertEquals(1, profileSkillRepository.findByProfileId(copied.getId()).size());

		ProfileLanguage copiedLanguage = profileLanguageRepository.findByProfileId(copied.getId()).get(0);
		ProfileLanguage sourceLanguage = profileLanguageRepository.findByProfileId(source.getId()).get(0);
		ProfileSkill copiedSkill = profileSkillRepository.findByProfileId(copied.getId()).get(0);
		ProfileSkill sourceSkill = profileSkillRepository.findByProfileId(source.getId()).get(0);
		assertNotEquals(sourceLanguage.getId(), copiedLanguage.getId());
		assertEquals(aggregate.language().getId(), copiedLanguage.getLanguage().getId());
		assertEquals(aggregate.language().getId(), sourceLanguage.getLanguage().getId());
		assertNotEquals(sourceSkill.getId(), copiedSkill.getId());
		assertEquals(aggregate.skill().getId(), copiedSkill.getSkill().getId());
		assertEquals(aggregate.skill().getId(), sourceSkill.getSkill().getId());
	}

	@Test
	void rejectsTrimmedCaseInsensitiveDuplicateName() {
		UserAccount user = saveUser();
		Profile source = saveProfile(user, "Copy Source-" + UUID.randomUUID());
		String existingName = "Existing-" + UUID.randomUUID();
		saveProfile(user, existingName);

		ApiException exception = assertThrows(ApiException.class,
				() -> profileCopyService.copy(user.getId(), source.getId(),
						new ProfileCopyRequest(" " + existingName.toLowerCase() + " ")));

		assertEquals(ErrorCode.PROFILE_NAME_ALREADY_EXISTS, exception.getErrorCode());
	}

	private void assertThrowsWithCode(Runnable action, ErrorCode errorCode) {
		ApiException exception = assertThrows(ApiException.class, action::run);
		assertEquals(errorCode, exception.getErrorCode());
	}

	private UserAccount saveUser() {
		return userRepository.saveAndFlush(new UserAccount(UUID.randomUUID() + "@example.com",
				"copy-member-" + UUID.randomUUID(), "hash", UserRole.MEMBER, UserStatus.ACTIVE));
	}

	private Profile saveProfile(UserAccount user, String name) {
		return profileRepository.saveAndFlush(new Profile(user, name, "First", "Last", "Engineer",
				new BigDecimal("3.5"), "Personality", "Summary"));
	}

	private CopiedAggregate savePopulatedAggregate(UserAccount user) {
		Profile source = saveProfile(user, "Source-" + UUID.randomUUID());
		ReflectionTestUtils.setField(source, "hasPreviewed", true);
		source = profileRepository.saveAndFlush(source);
		Language language = languageRepository.saveAndFlush(new Language("copy-language-" + UUID.randomUUID()));
		SkillCategory category = skillCategoryRepository.findByCode("BACKEND").orElseThrow();
		Skill skill = skillRepository.saveAndFlush(new Skill("copy-skill-" + UUID.randomUUID(), category));
		educationRepository.saveAndFlush(new Education(source, "School", "Degree", "Field", LocalDate.of(2020, 1, 1),
				LocalDate.of(2024, 1, 1), EducationStatus.COMPLETED));
		profileLanguageRepository.saveAndFlush(new ProfileLanguage(source, language, LanguageLevel.NATIVE));
		certificateRepository.saveAndFlush(new Certificate(source, "Certificate", LocalDate.of(2023, 5, 1)));
		projectRepository.saveAndFlush(new Project(source, "Project", "Description", LocalDate.of(2022, 1, 1),
				LocalDate.of(2023, 1, 1), ProjectStatus.COMPLETED, "Engineer", 4, "Responsibilities", "Java", "Tools"));
		profileSkillRepository.saveAndFlush(new ProfileSkill(source, skill, new BigDecimal("2.5"), LocalDate.of(2025, 1, 1)));

		ProfileResponse response = profileCopyService.copy(user.getId(), source.getId(),
				new ProfileCopyRequest("Copy-" + UUID.randomUUID()));
		Profile copied = profileRepository.findById(response.id()).orElseThrow();
		return new CopiedAggregate(source, copied, language, skill);
	}

	private record CopiedAggregate(Profile source, Profile copied, Language language, Skill skill) {
	}
}
