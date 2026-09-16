package com.fpt.ibom.cv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.cv.model.CvDocument;
import com.fpt.ibom.cv.model.CvEducationStatus;
import com.fpt.ibom.cv.model.CvLanguageLevel;
import com.fpt.ibom.cv.model.CvProjectStatus;
import com.fpt.ibom.cv.service.CvDocumentAssembler;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.master.entity.Language;
import com.fpt.ibom.master.entity.Skill;
import com.fpt.ibom.master.entity.SkillCategory;
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
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class CvDocumentAssemblerTest {

	private final ProfileRepository profiles = org.mockito.Mockito.mock(ProfileRepository.class);
	private final EducationRepository educations = org.mockito.Mockito.mock(EducationRepository.class);
	private final ProfileLanguageRepository profileLanguages = org.mockito.Mockito.mock(ProfileLanguageRepository.class);
	private final CertificateRepository certificates = org.mockito.Mockito.mock(CertificateRepository.class);
	private final ProjectRepository projects = org.mockito.Mockito.mock(ProjectRepository.class);
	private final ProfileSkillRepository profileSkills = org.mockito.Mockito.mock(ProfileSkillRepository.class);
	private final CvDocumentAssembler assembler = new CvDocumentAssembler(profiles, educations, profileLanguages,
			certificates, projects, profileSkills);

	@Test
	void assemblesAllProfileContentUsingEstablishedOrderAndMasterData() {
		Profile profile = profile();
		ReflectionTestUtils.setField(profile, "version", 4L);
		ReflectionTestUtils.setField(profile, "hasPreviewed", true);
		Education completedEducation = new Education(profile, "FPT University", "Bachelor", "Software Engineering",
				LocalDate.of(2018, 9, 1), LocalDate.of(2022, 6, 1), EducationStatus.COMPLETED);
		Education ongoingEducation = new Education(profile, "Cloud Academy", "Certificate", "Cloud Computing",
				LocalDate.of(2024, 1, 1), null, EducationStatus.ONGOING);
		ProfileLanguage beginnerZulu = profileLanguage(profile, language(11L, "zulu"), LanguageLevel.BEGINNER, 11L);
		ProfileLanguage nativeEnglish = profileLanguage(profile, language(12L, "English"), LanguageLevel.NATIVE, 12L);
		ProfileLanguage advancedAlpha = profileLanguage(profile, language(13L, "alpha"), LanguageLevel.ADVANCED, 13L);
		Certificate olderCertificate = new Certificate(profile, "AWS Associate", LocalDate.of(2023, 5, 1));
		Certificate newerCertificate = new Certificate(profile, "CKA", LocalDate.of(2025, 2, 1));
		Project ongoingProject = new Project(profile, "Current Platform", "Builds a platform", LocalDate.of(2025, 1, 1),
				null, ProjectStatus.ONGOING, "Developer", 4, "Builds services", "Java", "Spring");
		Project completedProject = new Project(profile, "Legacy Platform", "Maintained a platform", LocalDate.of(2021, 1, 1),
				LocalDate.of(2023, 12, 1), ProjectStatus.COMPLETED, "Engineer", 3, "Maintained services", "Java", "Docker");
		ProfileSkill beta = profileSkill(profile, skill(21L, "Beta", "FRONTEND", "Frontend"), "2.25", null, 21L);
		ProfileSkill zulu = profileSkill(profile, skill(22L, "Zulu", "BACKEND", "Backend"), "3.50",
				LocalDate.of(2025, 8, 1), 22L);
		ProfileSkill alpha = profileSkill(profile, skill(23L, "alpha", "DATABASE", "Database"), "3.50",
				LocalDate.of(2026, 1, 1), 23L);

		when(profiles.findByIdAndDeletedAtIsNull(8L)).thenReturn(Optional.of(profile));
		when(educations.findByProfileIdOrderByIdAsc(8L)).thenReturn(List.of(completedEducation, ongoingEducation));
		when(profileLanguages.findByProfileId(8L)).thenReturn(List.of(beginnerZulu, nativeEnglish, advancedAlpha));
		when(certificates.findByProfileIdOrderByIssueDateDescIdAsc(8L)).thenReturn(List.of(newerCertificate, olderCertificate));
		when(projects.findByProfileIdInDisplayOrder(8L)).thenReturn(List.of(ongoingProject, completedProject));
		when(profileSkills.findByProfileId(8L)).thenReturn(List.of(beta, zulu, alpha));

		CvDocument document = assembler.assemble(8L);

		assertEquals("First", document.personalDetails().firstName());
		assertEquals("Last", document.personalDetails().lastName());
		assertEquals("Engineer", document.personalDetails().jobTitle());
		assertEquals(new BigDecimal("7.50"), document.personalDetails().yearsOfExperience());
		assertEquals("Personality", document.personalDetails().personality());
		assertEquals("Technical summary", document.personalDetails().technicalSummary());

		assertEquals(List.of("FPT University", "Cloud Academy"),
				document.education().stream().map(education -> education.schoolName()).toList());
		assertEquals(List.of(CvEducationStatus.COMPLETED, CvEducationStatus.ONGOING),
				document.education().stream().map(education -> education.status()).toList());
		assertEquals("Software Engineering", document.education().get(0).fieldOfStudy());

		assertEquals(List.of("English", "alpha", "zulu"),
			document.languages().stream().map(language -> language.languageName()).toList());
		assertEquals(List.of(CvLanguageLevel.NATIVE, CvLanguageLevel.ADVANCED, CvLanguageLevel.BEGINNER),
			document.languages().stream().map(language -> language.level()).toList());

		assertEquals(List.of("CKA", "AWS Associate"),
			document.certificates().stream().map(certificate -> certificate.certificateName()).toList());
		assertEquals(LocalDate.of(2025, 2, 1), document.certificates().get(0).issueDate());

		assertEquals(List.of("Current Platform", "Legacy Platform"),
			document.projects().stream().map(project -> project.name()).toList());
		assertEquals(List.of(CvProjectStatus.ONGOING, CvProjectStatus.COMPLETED),
			document.projects().stream().map(project -> project.status()).toList());
		assertEquals("Builds services", document.projects().get(0).responsibilities());

		assertEquals(List.of("alpha", "Zulu", "Beta"),
			document.skills().stream().map(skill -> skill.skillName()).toList());
		assertEquals("DATABASE", document.skills().get(0).categoryCode());
		assertEquals("Database", document.skills().get(0).categoryName());
		assertEquals(new BigDecimal("3.50"), document.skills().get(0).experienceYears());
		assertEquals(LocalDate.of(2026, 1, 1), document.skills().get(0).lastUsed());

		assertTrue(profile.isHasPreviewed());
		assertEquals(4L, profile.getVersion());
		verify(profiles, never()).save(any(Profile.class));
		verify(profiles, never()).saveAndFlush(any(Profile.class));
		verify(educations, never()).save(any(Education.class));
		verify(profileLanguages, never()).save(any(ProfileLanguage.class));
		verify(certificates, never()).save(any(Certificate.class));
		verify(projects, never()).save(any(Project.class));
		verify(profileSkills, never()).save(any(ProfileSkill.class));
	}

	@Test
	void representsEmptySectionsAsNonNullImmutableLists() {
		when(profiles.findByIdAndDeletedAtIsNull(8L)).thenReturn(Optional.of(profile()));
		when(educations.findByProfileIdOrderByIdAsc(8L)).thenReturn(List.of());
		when(profileLanguages.findByProfileId(8L)).thenReturn(List.of());
		when(certificates.findByProfileIdOrderByIssueDateDescIdAsc(8L)).thenReturn(List.of());
		when(projects.findByProfileIdInDisplayOrder(8L)).thenReturn(List.of());
		when(profileSkills.findByProfileId(8L)).thenReturn(List.of());

		CvDocument document = assembler.assemble(8L);

		assertNotNull(document.education());
		assertNotNull(document.languages());
		assertNotNull(document.certificates());
		assertNotNull(document.projects());
		assertNotNull(document.skills());
		assertTrue(document.education().isEmpty());
		assertTrue(document.languages().isEmpty());
		assertTrue(document.certificates().isEmpty());
		assertTrue(document.projects().isEmpty());
		assertTrue(document.skills().isEmpty());
		assertThrows(UnsupportedOperationException.class, () -> document.education().add(null));
		assertThrows(UnsupportedOperationException.class, () -> document.languages().add(null));
		assertThrows(UnsupportedOperationException.class, () -> document.certificates().add(null));
		assertThrows(UnsupportedOperationException.class, () -> document.projects().add(null));
		assertThrows(UnsupportedOperationException.class, () -> document.skills().add(null));
	}

	@Test
	void missingOrSoftDeletedProfileFailsBeforeChildRepositoriesAreQueried() {
		when(profiles.findByIdAndDeletedAtIsNull(8L)).thenReturn(Optional.empty());

		ApiException exception = assertThrows(ApiException.class, () -> assembler.assemble(8L));

		assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
		assertEquals(ErrorCode.PROFILE_NOT_FOUND, exception.getErrorCode());
		verifyNoInteractions(educations, profileLanguages, certificates, projects, profileSkills);
	}

	private Profile profile() {
		return new Profile(user(), "Profile", "First", "Last", "Engineer", new BigDecimal("7.50"), "Personality",
				"Technical summary");
	}

	private UserAccount user() {
		return new UserAccount("user@example.com", "member", "hash", UserRole.MEMBER, UserStatus.ACTIVE);
	}

	private Language language(Long id, String name) {
		Language language = new Language(name);
		ReflectionTestUtils.setField(language, "id", id);
		return language;
	}

	private ProfileLanguage profileLanguage(Profile profile, Language language, LanguageLevel level, Long id) {
		ProfileLanguage profileLanguage = new ProfileLanguage(profile, language, level);
		ReflectionTestUtils.setField(profileLanguage, "id", id);
		return profileLanguage;
	}

	private Skill skill(Long id, String name, String code, String categoryName) {
		SkillCategory category = new SkillCategory(code, categoryName);
		ReflectionTestUtils.setField(category, "id", id + 100L);
		Skill skill = new Skill(name, category);
		ReflectionTestUtils.setField(skill, "id", id);
		return skill;
	}

	private ProfileSkill profileSkill(Profile profile, Skill skill, String experienceYears, LocalDate lastUsed, Long id) {
		ProfileSkill profileSkill = new ProfileSkill(profile, skill, new BigDecimal(experienceYears), lastUsed);
		ReflectionTestUtils.setField(profileSkill, "id", id);
		return profileSkill;
	}
}
