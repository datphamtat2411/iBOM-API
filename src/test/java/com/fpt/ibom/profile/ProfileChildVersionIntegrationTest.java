package com.fpt.ibom.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.fpt.ibom.MySqlIntegrationTest;
import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.master.entity.Language;
import com.fpt.ibom.master.entity.Skill;
import com.fpt.ibom.master.entity.SkillCategory;
import com.fpt.ibom.master.repository.LanguageRepository;
import com.fpt.ibom.master.repository.SkillCategoryRepository;
import com.fpt.ibom.master.repository.SkillRepository;
import com.fpt.ibom.profile.dto.CertificateMutationResponse;
import com.fpt.ibom.profile.dto.CertificateRequest;
import com.fpt.ibom.profile.dto.EducationMutationResponse;
import com.fpt.ibom.profile.dto.EducationRequest;
import com.fpt.ibom.profile.dto.ProfileLanguageMutationResponse;
import com.fpt.ibom.profile.dto.ProfileLanguageRequest;
import com.fpt.ibom.profile.dto.ProfileDetailResponse;
import com.fpt.ibom.profile.dto.ProfileSkillMutationResponse;
import com.fpt.ibom.profile.dto.ProfileSkillRequest;
import com.fpt.ibom.profile.dto.ProfileUpdateRequest;
import com.fpt.ibom.profile.dto.ProfileVersionResponse;
import com.fpt.ibom.profile.dto.ProjectMutationResponse;
import com.fpt.ibom.profile.dto.ProjectRequest;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.repository.CertificateRepository;
import com.fpt.ibom.profile.repository.EducationRepository;
import com.fpt.ibom.profile.repository.ProfileLanguageRepository;
import com.fpt.ibom.profile.repository.ProfileRepository;
import com.fpt.ibom.profile.repository.ProfileSkillRepository;
import com.fpt.ibom.profile.repository.ProjectRepository;
import com.fpt.ibom.profile.service.CertificateService;
import com.fpt.ibom.profile.service.EducationService;
import com.fpt.ibom.profile.service.ProfileLanguageService;
import com.fpt.ibom.profile.service.ProfileService;
import com.fpt.ibom.profile.service.ProfileSkillService;
import com.fpt.ibom.profile.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class ProfileChildVersionIntegrationTest extends MySqlIntegrationTest {

	@Autowired
	private EducationService educationService;
	@Autowired
	private ProfileLanguageService profileLanguageService;
	@Autowired
	private CertificateService certificateService;
	@Autowired
	private ProjectService projectService;
	@Autowired
	private ProfileSkillService profileSkillService;
	@Autowired
	private ProfileService profileService;
	@Autowired
	private UserAccountRepository userRepository;
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
	private SkillCategoryRepository skillCategoryRepository;
	@Autowired
	private SkillRepository skillRepository;
	@Autowired
	private PlatformTransactionManager transactionManager;

	@Test
	void advancesExactlyOnceAndChainsAcrossAllChildSections() {
		UserAccount user = saveUser();
		Profile profile = saveProfile(user, true);
		Language english = language("English");
		Language vietnamese = language("Vietnamese");
		Skill java = saveSkill("Java-" + UUID.randomUUID());
		Skill spring = saveSkill("Spring-" + UUID.randomUUID());
		long version = 0L;

		EducationMutationResponse education = educationService.create(user.getId(), profile.getId(),
				new EducationRequest("School", "Degree", "Field", LocalDate.of(2020, 1, 1), null, "ONGOING", version));
		version = assertPersistedVersion(profile, version, education.profileVersion());
		EducationMutationResponse updatedEducation = educationService.update(user.getId(), profile.getId(),
				education.education().id(), new EducationRequest("Updated School", "Degree", "Field",
						LocalDate.of(2020, 1, 1), LocalDate.of(2022, 1, 1), "COMPLETED", version));
		version = assertPersistedVersion(profile, version, updatedEducation.profileVersion());
		ProfileVersionResponse deletedEducation = educationService.delete(user.getId(), profile.getId(),
				education.education().id(), version);
		version = assertPersistedVersion(profile, version, deletedEducation.profileVersion());
		assertTrue(educationRepository.findById(education.education().id()).isEmpty());

		ProfileLanguageMutationResponse profileLanguage = profileLanguageService.create(user.getId(), profile.getId(),
				new ProfileLanguageRequest(english.getId(), "NATIVE", version));
		version = assertPersistedVersion(profile, version, profileLanguage.profileVersion());
		ProfileLanguageMutationResponse updatedProfileLanguage = profileLanguageService.update(user.getId(), profile.getId(),
				profileLanguage.profileLanguage().profileLanguageId(),
				new ProfileLanguageRequest(vietnamese.getId(), "ADVANCED", version));
		version = assertPersistedVersion(profile, version, updatedProfileLanguage.profileVersion());
		ProfileVersionResponse deletedProfileLanguage = profileLanguageService.delete(user.getId(), profile.getId(),
				profileLanguage.profileLanguage().profileLanguageId(), version);
		version = assertPersistedVersion(profile, version, deletedProfileLanguage.profileVersion());
		assertTrue(profileLanguageRepository.findById(profileLanguage.profileLanguage().profileLanguageId()).isEmpty());

		CertificateMutationResponse certificate = certificateService.create(user.getId(), profile.getId(),
				new CertificateRequest("AWS", LocalDate.of(2024, 1, 1), version));
		version = assertPersistedVersion(profile, version, certificate.profileVersion());
		CertificateMutationResponse updatedCertificate = certificateService.update(user.getId(), profile.getId(),
				certificate.certificate().id(), new CertificateRequest("GCP", LocalDate.of(2024, 2, 1), version));
		version = assertPersistedVersion(profile, version, updatedCertificate.profileVersion());
		ProfileVersionResponse deletedCertificate = certificateService.delete(user.getId(), profile.getId(),
				certificate.certificate().id(), version);
		version = assertPersistedVersion(profile, version, deletedCertificate.profileVersion());
		assertTrue(certificateRepository.findById(certificate.certificate().id()).isEmpty());

		ProjectMutationResponse project = projectService.create(user.getId(), profile.getId(), projectRequest("ONGOING", version));
		version = assertPersistedVersion(profile, version, project.profileVersion());
		ProjectMutationResponse updatedProject = projectService.update(user.getId(), profile.getId(), project.project().id(),
				projectRequest("COMPLETED", version));
		version = assertPersistedVersion(profile, version, updatedProject.profileVersion());
		ProfileVersionResponse deletedProject = projectService.delete(user.getId(), profile.getId(), project.project().id(), version);
		version = assertPersistedVersion(profile, version, deletedProject.profileVersion());
		assertTrue(projectRepository.findById(project.project().id()).isEmpty());

		ProfileSkillMutationResponse profileSkill = profileSkillService.create(user.getId(), profile.getId(),
				new ProfileSkillRequest(java.getId(), new BigDecimal("2.50"), LocalDate.of(2024, 1, 1), version));
		version = assertPersistedVersion(profile, version, profileSkill.profileVersion());
		ProfileSkillMutationResponse updatedProfileSkill = profileSkillService.update(user.getId(), profile.getId(),
				profileSkill.profileSkill().profileSkillId(),
				new ProfileSkillRequest(spring.getId(), new BigDecimal("3.50"), LocalDate.of(2025, 1, 1), version));
		version = assertPersistedVersion(profile, version, updatedProfileSkill.profileVersion());
		ProfileVersionResponse deletedProfileSkill = profileSkillService.delete(user.getId(), profile.getId(),
				profileSkill.profileSkill().profileSkillId(), version);
		version = assertPersistedVersion(profile, version, deletedProfileSkill.profileVersion());
		assertTrue(profileSkillRepository.findById(profileSkill.profileSkill().profileSkillId()).isEmpty());

		assertEquals(15L, version);
		assertFalse(profileRepository.findById(profile.getId()).orElseThrow().isHasPreviewed());
	}

	@Test
	void startsWithPreviewAlreadyFalseAndReturnedVersionCanBeUsedImmediately() {
		UserAccount user = saveUser();
		Profile profile = saveProfile(user, false);

		EducationMutationResponse first = educationService.create(user.getId(), profile.getId(),
				new EducationRequest("First School", "Degree", null, LocalDate.of(2020, 1, 1), null, "ONGOING", 0L));
		assertEquals(1L, first.profileVersion());
		assertEquals(1L, profileRepository.findById(profile.getId()).orElseThrow().getVersion());

		EducationMutationResponse second = educationService.create(user.getId(), profile.getId(),
				new EducationRequest("Second School", "Degree", null, LocalDate.of(2021, 1, 1), null, "ONGOING",
						first.profileVersion()));
		assertEquals(2L, second.profileVersion());
		Profile reloaded = profileRepository.findById(profile.getId()).orElseThrow();
		assertEquals(second.profileVersion(), reloaded.getVersion());
		assertFalse(reloaded.isHasPreviewed());
	}

	@Test
	void returnedVersionCanBeChainedInsideOneOuterTransactionAndPersistenceContext() {
		UserAccount user = saveUser();
		Profile profile = saveProfile(user, true);
		TransactionTemplate transaction = new TransactionTemplate(transactionManager);

		Long finalVersion = transaction.execute(status -> {
			EducationMutationResponse first = educationService.create(user.getId(), profile.getId(),
					new EducationRequest("Transactional School", "Degree", null, LocalDate.of(2020, 1, 1), null,
							"ONGOING", 0L));
			Profile managedAfterFirstMutation = profileRepository.findById(profile.getId()).orElseThrow();
			assertEquals(first.profileVersion(), managedAfterFirstMutation.getVersion());
			assertFalse(managedAfterFirstMutation.isHasPreviewed());

			CertificateMutationResponse second = certificateService.create(user.getId(), profile.getId(),
					new CertificateRequest("Transactional Certificate", LocalDate.of(2024, 1, 1),
							first.profileVersion()));
			assertEquals(first.profileVersion() + 1, second.profileVersion());
			assertEquals(second.profileVersion(), managedAfterFirstMutation.getVersion());
			return second.profileVersion();
		});

		Profile reloaded = profileRepository.findById(profile.getId()).orElseThrow();
		assertEquals(finalVersion, reloaded.getVersion());
		assertEquals(2L, reloaded.getVersion());
		assertFalse(reloaded.isHasPreviewed());
	}

	@Test
	void staleChildMutationDoesNotInvalidatePreview() {
		UserAccount user = saveUser();
		Profile profile = saveProfile(user, true);

		ApiException conflict = org.junit.jupiter.api.Assertions.assertThrows(ApiException.class,
				() -> educationService.create(user.getId(), profile.getId(), new EducationRequest("Stale School", "Degree",
						null, LocalDate.of(2020, 1, 1), null, "ONGOING", 1L)));

		assertEquals(ErrorCode.PROFILE_VERSION_CONFLICT, conflict.getErrorCode());
		Profile reloaded = profileRepository.findById(profile.getId()).orElseThrow();
		assertEquals(0L, reloaded.getVersion());
		assertTrue(reloaded.isHasPreviewed());
		assertTrue(educationRepository.findByProfileIdOrderByIdAsc(profile.getId()).isEmpty());
	}

	@Test
	void onlyOneConcurrentChildMutationUsingTheSameVersionCanCommit() throws Exception {
		UserAccount user = saveUser();
		Profile profile = saveProfile(user, false);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<Object> first = executor.submit(() -> attemptConcurrentCreate(start, user, profile, "Concurrent One"));
			Future<Object> second = executor.submit(() -> attemptConcurrentCreate(start, user, profile, "Concurrent Two"));
			start.countDown();

			Object firstResult = first.get();
			Object secondResult = second.get();
			List<Object> results = List.of(firstResult, secondResult);
			assertEquals(1L, results.stream().filter(EducationMutationResponse.class::isInstance).count());
			ApiException conflict = assertInstanceOf(ApiException.class,
					results.stream().filter(ApiException.class::isInstance).findFirst().orElseThrow());
			assertEquals(ErrorCode.PROFILE_VERSION_CONFLICT, conflict.getErrorCode());
			assertEquals(1L, profileRepository.findById(profile.getId()).orElseThrow().getVersion());
			assertEquals(1, educationRepository.findByProfileIdOrderByIdAsc(profile.getId()).size());
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void profileCoreUpdateAndChildMutationUsingTheSameVersionCannotBothCommit() throws Exception {
		UserAccount user = saveUser();
		Profile profile = saveProfile(user, true);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<Object> core = executor.submit(() -> attemptConcurrentCoreUpdate(start, user, profile));
			Future<Object> child = executor.submit(() -> attemptConcurrentCreate(start, user, profile, "Core Race School"));
			start.countDown();

			List<Object> results = List.of(core.get(), child.get());
			assertEquals(1L, results.stream().filter(ApiException.class::isInstance).count());
			assertEquals(1L, results.stream()
					.filter(result -> result instanceof ProfileDetailResponse || result instanceof EducationMutationResponse)
					.count());
			ApiException conflict = assertInstanceOf(ApiException.class,
					results.stream().filter(ApiException.class::isInstance).findFirst().orElseThrow());
			assertEquals(ErrorCode.PROFILE_VERSION_CONFLICT, conflict.getErrorCode());
			Profile reloaded = profileRepository.findById(profile.getId()).orElseThrow();
			assertEquals(1L, reloaded.getVersion());
			assertFalse(reloaded.isHasPreviewed());
			int expectedEducationCount = results.stream().anyMatch(EducationMutationResponse.class::isInstance) ? 1 : 0;
			assertEquals(expectedEducationCount,
					educationRepository.findByProfileIdOrderByIdAsc(profile.getId()).size());
		} finally {
			executor.shutdownNow();
		}
	}

	private Object attemptConcurrentCreate(CountDownLatch start, UserAccount user, Profile profile, String schoolName)
			throws InterruptedException {
		start.await();
		try {
			return educationService.create(user.getId(), profile.getId(), new EducationRequest(schoolName, "Degree", null,
					LocalDate.of(2020, 1, 1), null, "ONGOING", 0L));
		} catch (ApiException exception) {
			return exception;
		}
	}

	private Object attemptConcurrentCoreUpdate(CountDownLatch start, UserAccount user, Profile profile)
			throws InterruptedException {
		start.await();
		try {
			return profileService.update(user.getId(), profile.getId(), new ProfileUpdateRequest("Core Updated Profile",
					"First", "Last", "Engineer", BigDecimal.ONE, "Personality", "Summary", 0L));
		} catch (ApiException exception) {
			return exception;
		}
	}

	private long assertPersistedVersion(Profile profile, long previousVersion, long responseVersion) {
		assertEquals(previousVersion + 1, responseVersion);
		Profile reloaded = profileRepository.findById(profile.getId()).orElseThrow();
		assertEquals(responseVersion, reloaded.getVersion());
		assertFalse(reloaded.isHasPreviewed());
		return responseVersion;
	}

	private ProjectRequest projectRequest(String status, long version) {
		return new ProjectRequest("Project " + version, "Description", LocalDate.of(2020, 1, 1),
				"COMPLETED".equals(status) ? LocalDate.of(2022, 1, 1) : LocalDate.of(2025, 1, 1), status, "Engineer", 1,
				"Responsibilities", "Java", "Docker", version);
	}

	private UserAccount saveUser() {
		return userRepository.saveAndFlush(new UserAccount(UUID.randomUUID() + "@example.com", "member-" + UUID.randomUUID(),
				"hash", UserRole.MEMBER, UserStatus.ACTIVE));
	}

	private Profile saveProfile(UserAccount user, boolean hasPreviewed) {
		Profile profile = new Profile(user, "Profile-" + UUID.randomUUID(), "First", "Last", "Engineer", BigDecimal.ONE,
				"Personality", "Summary");
		ReflectionTestUtils.setField(profile, "hasPreviewed", hasPreviewed);
		return profileRepository.saveAndFlush(profile);
	}

	private Language language(String name) {
		return languageRepository.findAll().stream().filter(language -> name.equals(language.getName())).findFirst().orElseThrow();
	}

	private Skill saveSkill(String name) {
		SkillCategory category = skillCategoryRepository.findByCode("BACKEND").orElseThrow();
		return skillRepository.saveAndFlush(new Skill(name, category));
	}
}
