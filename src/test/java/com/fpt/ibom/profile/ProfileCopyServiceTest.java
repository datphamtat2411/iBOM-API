package com.fpt.ibom.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.profile.dto.ProfileCopyRequest;
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
import com.fpt.ibom.master.entity.Language;
import com.fpt.ibom.master.entity.Skill;
import com.fpt.ibom.master.entity.SkillCategory;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class ProfileCopyServiceTest {
	private final ProfileRepository profiles = org.mockito.Mockito.mock(ProfileRepository.class);
	private final EducationRepository educations = org.mockito.Mockito.mock(EducationRepository.class);
	private final ProfileLanguageRepository profileLanguages = org.mockito.Mockito.mock(ProfileLanguageRepository.class);
	private final CertificateRepository certificates = org.mockito.Mockito.mock(CertificateRepository.class);
	private final ProjectRepository projects = org.mockito.Mockito.mock(ProjectRepository.class);
	private final ProfileSkillRepository profileSkills = org.mockito.Mockito.mock(ProfileSkillRepository.class);
	private final ProfileCopyService service = new ProfileCopyService(profiles, educations, profileLanguages,
			certificates, projects, profileSkills);

	@Test
	void copiesAboutMeAndChildrenWithFreshStateAndSharedMasters() {
		UserAccount user = user();
		Profile source = new Profile(user, "Original", "First", "Last", "Engineer", new BigDecimal("3.5"),
				"Personality", "Technical summary");
		ReflectionTestUtils.setField(source, "id", 41L);
		ReflectionTestUtils.setField(source, "hasPreviewed", true);
		ReflectionTestUtils.setField(source, "version", 7L);
		ReflectionTestUtils.setField(source, "createdAt", Instant.parse("2026-01-01T00:00:00Z"));
		ReflectionTestUtils.setField(source, "updatedAt", Instant.parse("2026-01-02T00:00:00Z"));

		Education education = new Education(source, "School", "Degree", "Field", LocalDate.of(2020, 1, 1),
				LocalDate.of(2024, 1, 1), EducationStatus.COMPLETED);
		ReflectionTestUtils.setField(education, "id", 101L);
		Language language = new Language("English");
		ProfileLanguage profileLanguage = new ProfileLanguage(source, language, LanguageLevel.NATIVE);
		ReflectionTestUtils.setField(profileLanguage, "id", 102L);
		Certificate certificate = new Certificate(source, "Certificate", LocalDate.of(2023, 5, 1));
		ReflectionTestUtils.setField(certificate, "id", 103L);
		Project project = new Project(source, "Project", "Description", LocalDate.of(2022, 1, 1),
				LocalDate.of(2023, 1, 1), ProjectStatus.COMPLETED, "Engineer", 4, "Responsibilities", "Java",
				"Tools");
		ReflectionTestUtils.setField(project, "id", 104L);
		Skill skill = new Skill("Java", new SkillCategory("BACKEND", "Backend"));
		ProfileSkill profileSkill = new ProfileSkill(source, skill, new BigDecimal("2.5"), LocalDate.of(2025, 1, 1));
		ReflectionTestUtils.setField(profileSkill, "id", 105L);

		stubSource(source, false, List.of(education), List.of(profileLanguage), List.of(certificate), List.of(project),
				List.of(profileSkill));
		when(profiles.saveAndFlush(any(Profile.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ProfileResponse result = service.copy(7L, 41L, new ProfileCopyRequest(" Copied "));

		assertEquals("Copied", result.profileName());
		assertEquals("First", result.firstName());
		assertEquals("Last", result.lastName());
		assertEquals("Engineer", result.jobTitle());
		assertEquals(new BigDecimal("3.5"), result.yearsOfExperience());
		assertEquals("Personality", result.personality());
		assertEquals("Technical summary", result.technicalSummary());
		assertFalse(result.hasPreviewed());
		assertEquals(0L, result.version());

		org.mockito.ArgumentCaptor<Profile> profileCaptor = org.mockito.ArgumentCaptor.forClass(Profile.class);
		verify(profiles).saveAndFlush(profileCaptor.capture());
		Profile copied = profileCaptor.getValue();
		assertNotSame(source, copied);
		assertSame(user, copied.getUser());
		assertNull(copied.getId());
		assertEquals("Copied", copied.getProfileName());
		assertFalse(copied.isHasPreviewed());
		assertEquals(0L, copied.getVersion());
		assertNull(copied.getDeletedAt());
		assertNull(copied.getCreatedAt());
		assertNull(copied.getUpdatedAt());

		org.mockito.ArgumentCaptor<Iterable> educationCaptor = org.mockito.ArgumentCaptor.forClass(Iterable.class);
		verify(educations).saveAll(educationCaptor.capture());
		List<Education> copiedEducations = new ArrayList<>();
		educationCaptor.getValue().forEach(item -> copiedEducations.add((Education) item));
		assertEquals(1, copiedEducations.size());
		Education copiedEducation = copiedEducations.get(0);
		assertNotSame(education, copiedEducation);
		assertSame(copied, copiedEducation.getProfile());
		assertNull(copiedEducation.getId());
		assertEquals("School", copiedEducation.getSchoolName());
		assertEquals(EducationStatus.COMPLETED, copiedEducation.getStatus());

		org.mockito.ArgumentCaptor<Iterable> languageCaptor = org.mockito.ArgumentCaptor.forClass(Iterable.class);
		verify(profileLanguages).saveAll(languageCaptor.capture());
		List<ProfileLanguage> copiedLanguages = new ArrayList<>();
		languageCaptor.getValue().forEach(item -> copiedLanguages.add((ProfileLanguage) item));
		assertEquals(1, copiedLanguages.size());
		ProfileLanguage copiedLanguage = copiedLanguages.get(0);
		assertNotSame(profileLanguage, copiedLanguage);
		assertSame(copied, copiedLanguage.getProfile());
		assertSame(language, copiedLanguage.getLanguage());
		assertEquals(LanguageLevel.NATIVE, copiedLanguage.getLevel());
		assertNull(copiedLanguage.getId());

		org.mockito.ArgumentCaptor<Iterable> certificateCaptor = org.mockito.ArgumentCaptor.forClass(Iterable.class);
		verify(certificates).saveAll(certificateCaptor.capture());
		List<Certificate> copiedCertificates = new ArrayList<>();
		certificateCaptor.getValue().forEach(item -> copiedCertificates.add((Certificate) item));
		assertEquals(1, copiedCertificates.size());
		assertNotSame(certificate, copiedCertificates.get(0));
		assertSame(copied, copiedCertificates.get(0).getProfile());
		assertNull(copiedCertificates.get(0).getId());

		org.mockito.ArgumentCaptor<Iterable> projectCaptor = org.mockito.ArgumentCaptor.forClass(Iterable.class);
		verify(projects).saveAll(projectCaptor.capture());
		List<Project> copiedProjects = new ArrayList<>();
		projectCaptor.getValue().forEach(item -> copiedProjects.add((Project) item));
		assertEquals(1, copiedProjects.size());
		assertNotSame(project, copiedProjects.get(0));
		assertSame(copied, copiedProjects.get(0).getProfile());
		assertEquals("Responsibilities", copiedProjects.get(0).getResponsibilities());
		assertNull(copiedProjects.get(0).getId());

		org.mockito.ArgumentCaptor<Iterable> skillCaptor = org.mockito.ArgumentCaptor.forClass(Iterable.class);
		verify(profileSkills).saveAll(skillCaptor.capture());
		List<ProfileSkill> copiedSkills = new ArrayList<>();
		skillCaptor.getValue().forEach(item -> copiedSkills.add((ProfileSkill) item));
		assertEquals(1, copiedSkills.size());
		ProfileSkill copiedSkill = copiedSkills.get(0);
		assertNotSame(profileSkill, copiedSkill);
		assertSame(copied, copiedSkill.getProfile());
		assertSame(skill, copiedSkill.getSkill());
		assertEquals(new BigDecimal("2.5"), copiedSkill.getExperienceYears());
		assertNull(copiedSkill.getId());

		copied.update("Changed", "Changed", "Changed", "Changed", BigDecimal.ZERO, null, null);
		copiedEducation.update("Changed", "Degree", "Field", LocalDate.of(2020, 1, 1), null,
				EducationStatus.ONGOING);
		copiedLanguage.update(language, LanguageLevel.ADVANCED);
		copiedSkill.update(skill, BigDecimal.ONE, null);
		assertEquals("Original", source.getProfileName());
		assertEquals("School", education.getSchoolName());
		assertEquals(LanguageLevel.NATIVE, profileLanguage.getLevel());
		assertEquals(new BigDecimal("2.5"), profileSkill.getExperienceYears());
	}

	@Test
	void copiesProfileWithEmptySections() {
		Profile source = source();
		stubSource(source, false, List.of(), List.of(), List.of(), List.of(), List.of());
		when(profiles.saveAndFlush(any(Profile.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ProfileResponse result = service.copy(7L, 41L, new ProfileCopyRequest("Empty Copy"));

		assertEquals("Empty Copy", result.profileName());
		verify(educations).saveAll(argThat(items -> !items.iterator().hasNext()));
		verify(profileLanguages).saveAll(argThat(items -> !items.iterator().hasNext()));
		verify(certificates).saveAll(argThat(items -> !items.iterator().hasNext()));
		verify(projects).saveAll(argThat(items -> !items.iterator().hasNext()));
		verify(profileSkills).saveAll(argThat(items -> !items.iterator().hasNext()));
	}

	@Test
	void rejectsMissingOwnedActiveSource() {
		when(profiles.findByIdAndUserIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.empty());

		ApiException exception = assertThrows(ApiException.class,
				() -> service.copy(7L, 41L, new ProfileCopyRequest("Copied")));

		assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
		assertEquals(ErrorCode.PROFILE_NOT_FOUND, exception.getErrorCode());
		verify(profiles, never()).existsByUserIdAndDeletedAtIsNullAndProfileNameIgnoreCase(any(), any());
		verify(profiles, never()).saveAndFlush(any(Profile.class));
	}

	@Test
	void rejectsDuplicateTrimmedCaseInsensitiveName() {
		Profile source = source();
		when(profiles.findByIdAndUserIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(source));
		when(profiles.existsByUserIdAndDeletedAtIsNullAndProfileNameIgnoreCase(7L, "Copied")).thenReturn(true);

		ApiException exception = assertThrows(ApiException.class,
				() -> service.copy(7L, 41L, new ProfileCopyRequest(" Copied ")));

		assertEquals(HttpStatus.CONFLICT, exception.getStatus());
		assertEquals(ErrorCode.PROFILE_NAME_ALREADY_EXISTS, exception.getErrorCode());
		verify(profiles, never()).saveAndFlush(any(Profile.class));
	}

	@Test
	void translatesConcurrentProfileNameConstraintFailure() {
		Profile source = source();
		when(profiles.findByIdAndUserIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(source));
		when(profiles.existsByUserIdAndDeletedAtIsNullAndProfileNameIgnoreCase(7L, "Copied")).thenReturn(false);
		ConstraintViolationException violation = new ConstraintViolationException("duplicate", null,
				"uk_profiles_user_active_name");
		when(profiles.saveAndFlush(any(Profile.class)))
				.thenThrow(new DataIntegrityViolationException("duplicate", violation));

		ApiException exception = assertThrows(ApiException.class,
				() -> service.copy(7L, 41L, new ProfileCopyRequest("Copied")));

		assertEquals(ErrorCode.PROFILE_NAME_ALREADY_EXISTS, exception.getErrorCode());
		verify(educations, never()).findByProfileIdOrderByIdAsc(any());
	}

	private void stubSource(Profile source, boolean duplicate, List<Education> educationList,
			List<ProfileLanguage> languageList, List<Certificate> certificateList, List<Project> projectList,
			List<ProfileSkill> skillList) {
		when(profiles.findByIdAndUserIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(source));
		when(profiles.existsByUserIdAndDeletedAtIsNullAndProfileNameIgnoreCase(7L, "Copied"))
				.thenReturn(duplicate);
		when(educations.findByProfileIdOrderByIdAsc(41L)).thenReturn(educationList);
		when(profileLanguages.findByProfileId(41L)).thenReturn(languageList);
		when(certificates.findByProfileIdOrderByIssueDateDescIdAsc(41L)).thenReturn(certificateList);
		when(projects.findByProfileIdInDisplayOrder(41L)).thenReturn(projectList);
		when(profileSkills.findByProfileId(41L)).thenReturn(skillList);
	}

	private Profile source() {
		Profile source = new Profile(user(), "Original", "First", "Last", "Engineer", new BigDecimal("3.5"),
				"Personality", "Technical summary");
		ReflectionTestUtils.setField(source, "id", 41L);
		return source;
	}

	private UserAccount user() {
		return new UserAccount("user@example.com", "member", "hash", UserRole.MEMBER, UserStatus.ACTIVE);
	}
}
