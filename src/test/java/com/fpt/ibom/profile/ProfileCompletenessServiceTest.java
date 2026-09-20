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
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
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
import com.fpt.ibom.profile.service.ProfileCompletenessService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class ProfileCompletenessServiceTest {

	private final ProfileRepository profiles = org.mockito.Mockito.mock(ProfileRepository.class);
	private final EducationRepository educations = org.mockito.Mockito.mock(EducationRepository.class);
	private final ProfileLanguageRepository languages = org.mockito.Mockito.mock(ProfileLanguageRepository.class);
	private final CertificateRepository certificates = org.mockito.Mockito.mock(CertificateRepository.class);
	private final ProjectRepository projects = org.mockito.Mockito.mock(ProjectRepository.class);
	private final ProfileSkillRepository skills = org.mockito.Mockito.mock(ProfileSkillRepository.class);
	private final ProfileCompletenessService service = new ProfileCompletenessService(profiles, educations, languages,
			certificates, projects, skills);

	@Test
	void computesFourValidAboutMeFieldsWithoutUsingProfileName() {
		Profile profile = profile("Profile name", "First", "Last", "Engineer", BigDecimal.ONE, null, null);
		stubProfile(profile);

		ProfileCompletenessResponse result = service.get(7L, 8L);

		assertEquals(13, result.percentage());
		assertFalse(result.completed());
		assertEquals(4, aboutMe(result).validFieldCount());
		assertEquals(6, aboutMe(result).fieldCount());
	}

	@Test
	void computesFiveValidAboutMeFieldsAndRoundsOnlyFinalPercentage() {
		Profile profile = profile("Profile name", "First", "Last", "Engineer", BigDecimal.ONE, "Personality", null);
		stubProfile(profile);

		ProfileCompletenessResponse result = service.get(7L, 8L);

		assertEquals(17, result.percentage());
		assertEquals(5, aboutMe(result).validFieldCount());
		assertFalse(result.completed());
	}

	@Test
	void computesSixValidAboutMeFieldsAndLeavesAllCollectionWeightsIndependent() {
		Profile profile = profile("Profile name", "First", "Last", "Engineer", BigDecimal.ZERO, "Personality",
				"Summary");
		stubProfile(profile);

		ProfileCompletenessResponse result = service.get(7L, 8L);

		assertEquals(20, result.percentage());
		assertFalse(result.completed());
		assertTrue(result.sections().stream().skip(1).noneMatch(ProfileCompletenessSectionResponse::completed));
	}

	@Test
	void addsEachCollectionWeightForOneQualifyingRecordWithoutLoadingCollections() {
		Profile profile = profile("Profile name", "First", "Last", "Engineer", BigDecimal.ZERO, "Personality",
				"Summary");
		stubProfile(profile);
		when(educations.existsByProfileId(8L)).thenReturn(true);
		when(languages.existsByProfileId(8L)).thenReturn(true);
		when(certificates.existsByProfileId(8L)).thenReturn(true);
		when(projects.existsByProfileId(8L)).thenReturn(true);
		when(skills.existsByProfileId(8L)).thenReturn(true);

		ProfileCompletenessResponse result = service.get(7L, 8L);

		assertEquals(100, result.percentage());
		assertTrue(result.completed());
		assertEquals(List.of("aboutMe", "education", "language", "certificate", "project", "skills"),
				result.sections().stream().map(ProfileCompletenessSectionResponse::key).toList());
		assertEquals(List.of(20, 20, 15, 15, 20, 10),
				result.sections().stream().map(ProfileCompletenessSectionResponse::weight).toList());
		assertTrue(result.sections().stream().skip(1).allMatch(ProfileCompletenessSectionResponse::hasQualifyingRecord));
		verify(educations, never()).findByProfileIdOrderByIdAsc(8L);
		verify(languages, never()).findByProfileId(8L);
		verify(certificates, never()).findByProfileIdOrderByIssueDateDescIdAsc(8L);
		verify(projects, never()).findByProfileIdInDisplayOrder(8L);
		verify(skills, never()).findByProfileId(8L);
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
	void countsCompletedProfilesUsingBatchChildLookups() {
		Profile first = profile("First", "First", "Last", "Engineer", BigDecimal.ZERO, "Personality",
				"Summary");
		Profile second = profile("Second", "First", "Last", "Engineer", BigDecimal.ZERO, "Personality",
				"Summary");
		Profile incomplete = profile("Incomplete", "First", "Last", "Engineer", BigDecimal.ZERO, "Personality",
				null);
		ReflectionTestUtils.setField(second, "id", 9L);
		ReflectionTestUtils.setField(incomplete, "id", 10L);
		List<Long> profileIds = List.of(8L, 9L, 10L);
		when(educations.findProfileIdsByProfileIdIn(profileIds)).thenReturn(List.of(8L, 9L));
		when(languages.findProfileIdsByProfileIdIn(profileIds)).thenReturn(List.of(8L, 9L, 10L));
		when(certificates.findProfileIdsByProfileIdIn(profileIds)).thenReturn(List.of(8L, 9L));
		when(projects.findProfileIdsByProfileIdIn(profileIds)).thenReturn(List.of(8L, 9L));
		when(skills.findProfileIdsByProfileIdIn(profileIds)).thenReturn(List.of(8L, 9L));

		assertEquals(2, service.countCompleted(List.of(first, second, incomplete)));

		verify(educations, never()).existsByProfileId(any());
		verify(languages, never()).existsByProfileId(any());
		verify(certificates, never()).existsByProfileId(any());
		verify(projects, never()).existsByProfileId(any());
		verify(skills, never()).existsByProfileId(any());
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
