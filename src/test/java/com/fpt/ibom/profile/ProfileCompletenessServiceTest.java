package com.fpt.ibom.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.master.entity.Language;
import com.fpt.ibom.master.entity.Skill;
import com.fpt.ibom.master.entity.SkillCategory;
import com.fpt.ibom.profile.dto.ProfileCompletenessResponse;
import com.fpt.ibom.profile.dto.ProfileCompletenessSectionResponse;
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
import com.fpt.ibom.profile.service.ProfileCompletenessService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class ProfileCompletenessServiceTest {
	private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
	private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-10T18:30:00Z"), BUSINESS_ZONE);
	private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 9, 11);

	private final ProfileRepository profiles = org.mockito.Mockito.mock(ProfileRepository.class);
	private final EducationRepository educations = org.mockito.Mockito.mock(EducationRepository.class);
	private final ProfileLanguageRepository languages = org.mockito.Mockito.mock(ProfileLanguageRepository.class);
	private final CertificateRepository certificates = org.mockito.Mockito.mock(CertificateRepository.class);
	private final ProjectRepository projects = org.mockito.Mockito.mock(ProjectRepository.class);
	private final ProfileSkillRepository skills = org.mockito.Mockito.mock(ProfileSkillRepository.class);
	private final ProfileCompletenessService service = new ProfileCompletenessService(profiles, educations, languages,
			certificates, projects, skills, FIXED_CLOCK);

	@Test
	void computesFourValidAboutMeFieldsWithoutUsingProfileName() {
		Profile profile = profile("Profile name", "First", "Last", "Engineer", BigDecimal.ONE, null, null);
		stubProfile(profile);

		ProfileCompletenessResponse result = service.get(7L, 8L);

		assertEquals(new BigDecimal("13.33"), result.percentage());
		assertFalse(result.completed());
		assertEquals(4, aboutMe(result).validFieldCount());
		assertEquals(6, aboutMe(result).fieldCount());
	}

	@Test
	void computesFiveValidAboutMeFieldsWithTwoDecimalPlaces() {
		Profile profile = profile("Profile name", "First", "Last", "Engineer", BigDecimal.ONE, "Personality", null);
		stubProfile(profile);

		ProfileCompletenessResponse result = service.get(7L, 8L);

		assertEquals(new BigDecimal("16.67"), result.percentage());
		assertEquals(5, aboutMe(result).validFieldCount());
		assertFalse(result.completed());
	}

	@Test
	void computesSixValidAboutMeFieldsAndLeavesAllCollectionWeightsIndependent() {
		Profile profile = profile("Profile name", "First", "Last", "Engineer", BigDecimal.ZERO, "Personality",
				"Summary");
		stubProfile(profile);

		ProfileCompletenessResponse result = service.get(7L, 8L);

		assertEquals(new BigDecimal("20.00"), result.percentage());
		assertFalse(result.completed());
		assertTrue(result.sections().stream().skip(1).noneMatch(ProfileCompletenessSectionResponse::completed));
	}

	@Test
	void addsEachCollectionWeightWhenMixedRowsIncludeOneQualifyingRecord() {
		Profile profile = profile("Profile name", "First", "Last", "Engineer", BigDecimal.ZERO, "Personality",
				"Summary");
		stubProfile(profile);
		when(educations.findByProfileIdOrderByIdAsc(8L)).thenReturn(List.of(invalidEducation(profile), validEducation(profile)));
		when(languages.findByProfileId(8L)).thenReturn(List.of(invalidLanguage(profile), validLanguage(profile)));
		when(certificates.findByProfileIdOrderByIssueDateDescIdAsc(8L))
				.thenReturn(List.of(invalidCertificate(profile), validCertificate(profile)));
		when(projects.findByProfileIdInDisplayOrder(8L)).thenReturn(List.of(invalidProject(profile), validProject(profile)));
		when(skills.findByProfileId(8L)).thenReturn(List.of(invalidSkill(profile), validSkill(profile)));

		ProfileCompletenessResponse result = service.get(7L, 8L);

		assertEquals(new BigDecimal("100.00"), result.percentage());
		assertTrue(result.completed());
		assertEquals(List.of("aboutMe", "education", "language", "certificate", "project", "skills"),
				result.sections().stream().map(ProfileCompletenessSectionResponse::key).toList());
		assertEquals(List.of(20, 20, 15, 15, 20, 10),
				result.sections().stream().map(ProfileCompletenessSectionResponse::weight).toList());
		assertTrue(result.sections().stream().skip(1).allMatch(ProfileCompletenessSectionResponse::hasQualifyingRecord));
	}

	@Test
	void ignoresMalformedRowsWithoutCompletingAnyCollectionSection() {
		Profile profile = profile("Profile name", "First", "Last", "Engineer", BigDecimal.ZERO, "Personality",
				"Summary");
		stubProfile(profile);
		when(educations.findByProfileIdOrderByIdAsc(8L)).thenReturn(List.of(invalidEducation(profile)));
		when(languages.findByProfileId(8L)).thenReturn(List.of(invalidLanguage(profile)));
		when(certificates.findByProfileIdOrderByIssueDateDescIdAsc(8L)).thenReturn(List.of(invalidCertificate(profile)));
		when(projects.findByProfileIdInDisplayOrder(8L)).thenReturn(List.of(invalidProject(profile)));
		when(skills.findByProfileId(8L)).thenReturn(List.of(invalidSkill(profile)));

		ProfileCompletenessResponse result = service.get(7L, 8L);

		assertEquals(new BigDecimal("20.00"), result.percentage());
		assertFalse(result.completed());
		assertTrue(result.sections().stream().skip(1).noneMatch(ProfileCompletenessSectionResponse::completed));
	}

	@Test
	void rejectsMissingForeignOrDeletedProfileBeforeChildQueries() {
		when(profiles.findByIdAndUserIdAndDeletedAtIsNull(8L, 7L)).thenReturn(Optional.empty());

		ApiException exception = assertThrows(ApiException.class, () -> service.get(7L, 8L));

		assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
		assertEquals(ErrorCode.PROFILE_NOT_FOUND, exception.getErrorCode());
		verifyNoInteractions(educations, languages, certificates, projects, skills);
	}

	@Test
	void doesNotMutateProfileStateWhileCalculating() {
		Profile profile = profile("Profile name", "First", "Last", "Engineer", BigDecimal.ZERO, "Personality",
				"Summary");
		Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
		Instant updatedAt = Instant.parse("2026-01-02T00:00:00Z");
		ReflectionTestUtils.setField(profile, "version", 4L);
		ReflectionTestUtils.setField(profile, "hasPreviewed", true);
		ReflectionTestUtils.setField(profile, "createdAt", createdAt);
		ReflectionTestUtils.setField(profile, "updatedAt", updatedAt);
		stubProfile(profile);

		service.get(7L, 8L);

		assertEquals(4L, profile.getVersion());
		assertTrue(profile.isHasPreviewed());
		assertEquals(createdAt, profile.getCreatedAt());
		assertEquals(updatedAt, profile.getUpdatedAt());
		verify(profiles, never()).save(any());
	}

	@Test
	void countsCompletedProfilesWithSameQualifyingSemanticsAsSingleCalculation() {
		Profile first = profile("First", "First", "Last", "Engineer", BigDecimal.ZERO, "Personality",
				"Summary");
		Profile second = profile("Second", "First", "Last", "Engineer", BigDecimal.ZERO, "Personality",
				null);
		Profile incomplete = profile("Incomplete", "First", "Last", "Engineer", BigDecimal.ZERO, "Personality",
				null);
		ReflectionTestUtils.setField(second, "id", 9L);
		ReflectionTestUtils.setField(incomplete, "id", 10L);
		List<Long> profileIds = List.of(8L, 9L, 10L);
		List<Profile> profilesToCount = List.of(first, second, incomplete);
		when(educations.findByProfileIdOrderByIdAsc(8L)).thenReturn(List.of(validEducation(first)));
		when(educations.findByProfileIdOrderByIdAsc(9L)).thenReturn(List.of(invalidEducation(second)));
		when(educations.findByProfileIdOrderByIdAsc(10L)).thenReturn(List.of(validEducation(incomplete)));
		when(languages.findByProfileId(8L)).thenReturn(List.of(validLanguage(first)));
		when(languages.findByProfileId(9L)).thenReturn(List.of(invalidLanguage(second)));
		when(languages.findByProfileId(10L)).thenReturn(List.of(validLanguage(incomplete)));
		when(certificates.findByProfileIdOrderByIssueDateDescIdAsc(8L)).thenReturn(List.of(validCertificate(first)));
		when(certificates.findByProfileIdOrderByIssueDateDescIdAsc(9L)).thenReturn(List.of(invalidCertificate(second)));
		when(certificates.findByProfileIdOrderByIssueDateDescIdAsc(10L)).thenReturn(List.of(validCertificate(incomplete)));
		when(projects.findByProfileIdInDisplayOrder(8L)).thenReturn(List.of(validProject(first)));
		when(projects.findByProfileIdInDisplayOrder(9L)).thenReturn(List.of(invalidProject(second)));
		when(projects.findByProfileIdInDisplayOrder(10L)).thenReturn(List.of(validProject(incomplete)));
		when(skills.findByProfileId(8L)).thenReturn(List.of(validSkill(first)));
		when(skills.findByProfileId(9L)).thenReturn(List.of(invalidSkill(second)));
		when(skills.findByProfileId(10L)).thenReturn(List.of(validSkill(incomplete)));

		int expected = (int) profilesToCount.stream().filter(profile -> service.calculate(profile).completed()).count();

		when(educations.findByProfileIdIn(profileIds))
				.thenReturn(List.of(validEducation(first), invalidEducation(second), validEducation(incomplete)));
		when(languages.findByProfileIdIn(profileIds))
				.thenReturn(List.of(validLanguage(first), invalidLanguage(second), validLanguage(incomplete)));
		when(certificates.findByProfileIdIn(profileIds))
				.thenReturn(List.of(validCertificate(first), invalidCertificate(second), validCertificate(incomplete)));
		when(projects.findByProfileIdIn(profileIds))
				.thenReturn(List.of(validProject(first), invalidProject(second), validProject(incomplete)));
		when(skills.findByProfileIdIn(profileIds))
				.thenReturn(List.of(validSkill(first), invalidSkill(second), validSkill(incomplete)));

		assertEquals(expected, service.countCompleted(profilesToCount));
		assertEquals(1, expected);

		verify(educations, never()).existsByProfileId(any());
		verify(languages, never()).existsByProfileId(any());
		verify(certificates, never()).existsByProfileId(any());
		verify(projects, never()).existsByProfileId(any());
		verify(skills, never()).existsByProfileId(any());
		verify(educations).findByProfileIdIn(profileIds);
		verify(languages).findByProfileIdIn(profileIds);
		verify(certificates).findByProfileIdIn(profileIds);
		verify(projects).findByProfileIdIn(profileIds);
		verify(skills).findByProfileIdIn(profileIds);
	}

	@Test
	void returnsZeroWithoutBatchChildQueriesForEmptyProfiles() {
		assertEquals(0, service.countCompleted(List.of()));

		verifyNoInteractions(educations, languages, certificates, projects, skills);
	}

	private void stubProfile(Profile profile) {
		when(profiles.findByIdAndUserIdAndDeletedAtIsNull(8L, 7L)).thenReturn(Optional.of(profile));
	}

	private ProfileCompletenessSectionResponse aboutMe(ProfileCompletenessResponse response) {
		return response.sections().get(0);
	}

	private Education validEducation(Profile profile) {
		return new Education(profile, "School", "Degree", null, LocalDate.of(2020, 1, 1), null,
				EducationStatus.ONGOING);
	}

	private Education invalidEducation(Profile profile) {
		return new Education(profile, " ", null, null, null, null, null);
	}

	private ProfileLanguage validLanguage(Profile profile) {
		return new ProfileLanguage(profile, language(1L, "English"), LanguageLevel.ADVANCED);
	}

	private ProfileLanguage invalidLanguage(Profile profile) {
		return new ProfileLanguage(profile, null, null);
	}

	private Certificate validCertificate(Profile profile) {
		return new Certificate(profile, "AWS", BUSINESS_DATE);
	}

	private Certificate invalidCertificate(Profile profile) {
		return new Certificate(profile, " ", BUSINESS_DATE.plusDays(1));
	}

	private Project validProject(Profile profile) {
		return new Project(profile, "Project", "Description", LocalDate.of(2020, 1, 1), null, ProjectStatus.ONGOING,
				"Engineer", 1, null, null, null);
	}

	private Project invalidProject(Profile profile) {
		return new Project(profile, " ", null, LocalDate.of(2021, 1, 1), LocalDate.of(2020, 1, 1),
				ProjectStatus.COMPLETED, null, 0, null, null, null);
	}

	private ProfileSkill validSkill(Profile profile) {
		return new ProfileSkill(profile, skill(1L, "Java"), BigDecimal.ZERO, BUSINESS_DATE);
	}

	private ProfileSkill invalidSkill(Profile profile) {
		return new ProfileSkill(profile, null, BigDecimal.valueOf(-1), BUSINESS_DATE.plusDays(1));
	}

	private Language language(Long id, String name) {
		Language language = new Language(name);
		ReflectionTestUtils.setField(language, "id", id);
		return language;
	}

	private Skill skill(Long id, String name) {
		Skill skill = new Skill(name, new SkillCategory("BACKEND", "Backend"));
		ReflectionTestUtils.setField(skill, "id", id);
		return skill;
	}

	private Profile profile(String profileName, String firstName, String lastName, String jobTitle,
			BigDecimal yearsOfExperience, String personality, String technicalSummary) {
		Profile profile = new Profile(user(), profileName, firstName, lastName, jobTitle, yearsOfExperience, personality,
				technicalSummary);
		ReflectionTestUtils.setField(profile, "id", 8L);
		return profile;
	}

	private UserAccount user() {
		return new UserAccount("user@example.com", "member", "hash", UserRole.MEMBER, UserStatus.ACTIVE);
	}
}
