package com.fpt.ibom.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.master.entity.Skill;
import com.fpt.ibom.master.entity.SkillCategory;
import com.fpt.ibom.master.repository.SkillRepository;
import com.fpt.ibom.profile.dto.ProfileSkillMutationResponse;
import com.fpt.ibom.profile.dto.ProfileSkillRequest;
import com.fpt.ibom.profile.dto.ProfileSkillResponse;
import com.fpt.ibom.profile.dto.ProfileVersionResponse;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.entity.ProfileSkill;
import com.fpt.ibom.profile.repository.ProfileSkillRepository;
import com.fpt.ibom.profile.service.ProfileAccessService;
import com.fpt.ibom.profile.service.ProfileSkillService;
import com.fpt.ibom.profile.service.ProfileVersionService;
import jakarta.persistence.OptimisticLockException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class ProfileSkillServiceTest {

	private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
	private static final Instant FIXED_INSTANT = Instant.parse("2026-09-10T18:30:00Z");
	private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, BUSINESS_ZONE);
	private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 9, 11);

	private final ProfileSkillRepository profileSkills = org.mockito.Mockito.mock(ProfileSkillRepository.class);
	private final ProfileAccessService profileAccess = org.mockito.Mockito.mock(ProfileAccessService.class);
	private final SkillRepository skills = org.mockito.Mockito.mock(SkillRepository.class);
	private final ProfileVersionService profileVersions = org.mockito.Mockito.mock(ProfileVersionService.class);
	private final UserPrincipal principal = new UserPrincipal(7L, "user@example.com", "member", UserRole.MEMBER);
	private final ProfileSkillService service = new ProfileSkillService(profileSkills, profileAccess, skills, profileVersions,
			FIXED_CLOCK);

	@Test
	void listsOwnedSkillsByExperienceDescendingThenCaseInsensitiveSkillNameAndId() {
		Profile profile = profile();
		ProfileSkill zulu = profileSkill(profile, skill(12L, "Zulu"), 12L, "3.50", null);
		ProfileSkill alpha = profileSkill(profile, skill(13L, "alpha"), 13L, "3.50", null);
		ProfileSkill beta = profileSkill(profile, skill(14L, "Beta"), 14L, "2.25", null);
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(profileSkills.findByProfileId(8L)).thenReturn(List.of(beta, zulu, alpha));

		List<ProfileSkillResponse> result = service.list(principal, 8L);

		assertEquals(List.of("alpha", "Zulu", "Beta"), result.stream().map(ProfileSkillResponse::skillName).toList());
		assertEquals(List.of(new BigDecimal("3.50"), new BigDecimal("3.50"), new BigDecimal("2.25")),
				result.stream().map(ProfileSkillResponse::experienceYears).toList());
	}

	@Test
	void createsExistingSkillWithDecimalExperienceAndInvalidatesPreview() {
		Profile profile = profile();
		ReflectionTestUtils.setField(profile, "hasPreviewed", true);
		Skill skill = skill(22L, "Java");
		LocalDate lastUsed = BUSINESS_DATE;
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(skills.findById(22L)).thenReturn(Optional.of(skill));
		when(profileSkills.saveAndFlush(any(ProfileSkill.class))).thenAnswer(invocation -> invocation.getArgument(0));
		simulateVersionIncrementOnAdvance(profile);

		ProfileSkillMutationResponse result = service.create(principal, 8L,
				new ProfileSkillRequest(22L, new BigDecimal("2.75"), lastUsed, 0L));

		ArgumentCaptor<ProfileSkill> captor = ArgumentCaptor.forClass(ProfileSkill.class);
		verify(profileSkills).saveAndFlush(captor.capture());
		ProfileSkill saved = captor.getValue();
		assertEquals(skill, saved.getSkill());
		assertEquals(new BigDecimal("2.75"), saved.getExperienceYears());
		assertEquals(lastUsed, saved.getLastUsed());
		assertEquals("Backend", result.profileSkill().categoryName());
		assertFalse(profile.isHasPreviewed());
		assertEquals(1L, result.profileVersion());
		verify(profileVersions).advance(profile);
	}

	@Test
	void allowsFractionalExperienceWithoutComparingProfileExperience() {
		Profile profile = profile();
		Skill skill = skill(22L, "Java");
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(skills.findById(22L)).thenReturn(Optional.of(skill));
		when(profileSkills.saveAndFlush(any(ProfileSkill.class))).thenAnswer(invocation -> invocation.getArgument(0));
		simulateVersionIncrementOnAdvance(profile);

		ProfileSkillMutationResponse result = service.create(principal, 8L,
				new ProfileSkillRequest(22L, new BigDecimal("10.25"), null, 0L));

		assertEquals(new BigDecimal("10.25"), result.profileSkill().experienceYears());
	}

	@Test
	void rejectsMissingOrNegativeExperienceBeforeMutation() {
		Profile profile = profile();
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);

		ApiException negative = assertThrows(ApiException.class, () -> service.create(principal, 8L,
				new ProfileSkillRequest(22L, new BigDecimal("-0.01"), null, 0L)));
		ApiException missing = assertThrows(ApiException.class, () -> service.create(principal, 8L,
				new ProfileSkillRequest(22L, null, null, 0L)));

		assertEquals(HttpStatus.BAD_REQUEST, negative.getStatus());
		assertEquals(ErrorCode.VALIDATION_ERROR, negative.getErrorCode());
		assertEquals(ErrorCode.VALIDATION_ERROR, missing.getErrorCode());
		verify(profileVersions, never()).advance(any());
		verify(skills, never()).findById(any());
	}

	@Test
	void rejectsFutureLastUsedDateBeforeMutation() {
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile());

		ApiException exception = assertThrows(ApiException.class, () -> service.create(principal, 8L,
				new ProfileSkillRequest(22L, new BigDecimal("1.00"), BUSINESS_DATE.plusDays(1), 0L)));

		assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
		assertEquals(ErrorCode.PROFILE_SKILL_LAST_USED_IN_FUTURE, exception.getErrorCode());
		verify(profileVersions, never()).advance(any());
		verify(skills, never()).findById(any());
	}

	@Test
	void rejectsMissingSkillAndDuplicateAssignment() {
		Profile profile = profile();
		Skill skill = skill(22L, "Java");
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(skills.findById(22L)).thenReturn(Optional.empty());

		ApiException missing = assertThrows(ApiException.class, () -> service.create(principal, 8L,
				new ProfileSkillRequest(22L, new BigDecimal("1.00"), null, 0L)));
		assertEquals(ErrorCode.SKILL_NOT_FOUND, missing.getErrorCode());

		when(skills.findById(22L)).thenReturn(Optional.of(skill));
		when(profileSkills.existsByProfileIdAndSkillId(8L, 22L)).thenReturn(true);
		ApiException duplicate = assertThrows(ApiException.class, () -> service.create(principal, 8L,
				new ProfileSkillRequest(22L, new BigDecimal("1.00"), null, 0L)));
		assertEquals(ErrorCode.PROFILE_SKILL_ALREADY_EXISTS, duplicate.getErrorCode());
		verify(profileSkills, never()).saveAndFlush(any());
	}

	@Test
	void updatesSkillExperienceAndLastUsedUsingProfileScopedAssociationLookup() {
		Profile profile = profile();
		ProfileSkill association = profileSkill(profile, skill(22L, "Java"), 12L, "1.00", null);
		Skill replacement = skill(23L, "Spring");
		LocalDate lastUsed = LocalDate.of(2024, 12, 1);
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(profileSkills.findByIdAndProfileId(12L, 8L)).thenReturn(Optional.of(association));
		when(skills.findById(23L)).thenReturn(Optional.of(replacement));
		when(profileSkills.existsByProfileIdAndSkillIdAndIdNot(8L, 23L, 12L)).thenReturn(false);
		when(profileSkills.saveAndFlush(association)).thenReturn(association);
		simulateVersionIncrementOnAdvance(profile);

		ProfileSkillMutationResponse result = service.update(principal, 8L, 12L,
				new ProfileSkillRequest(23L, new BigDecimal("4.25"), lastUsed, 0L));

		assertEquals(replacement, association.getSkill());
		assertEquals(new BigDecimal("4.25"), association.getExperienceYears());
		assertEquals(lastUsed, association.getLastUsed());
		assertEquals(1L, result.profileVersion());
		verify(profileSkills).findByIdAndProfileId(12L, 8L);
		verify(profileSkills).existsByProfileIdAndSkillIdAndIdNot(8L, 23L, 12L);
	}

	@Test
	void rejectsDuplicateReplacementDuringUpdate() {
		Profile profile = profile();
		ProfileSkill association = profileSkill(profile, skill(22L, "Java"), 12L, "1.00", null);
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(profileSkills.findByIdAndProfileId(12L, 8L)).thenReturn(Optional.of(association));
		when(skills.findById(23L)).thenReturn(Optional.of(skill(23L, "Spring")));
		when(profileSkills.existsByProfileIdAndSkillIdAndIdNot(8L, 23L, 12L)).thenReturn(true);

		ApiException exception = assertThrows(ApiException.class, () -> service.update(principal, 8L, 12L,
				new ProfileSkillRequest(23L, new BigDecimal("4.25"), null, 0L)));

		assertEquals(ErrorCode.PROFILE_SKILL_ALREADY_EXISTS, exception.getErrorCode());
		verify(profileVersions, never()).advance(any());
		verify(profileSkills, never()).saveAndFlush(any());
	}

	@Test
	void physicallyDeletesOwnedAssociationAndReturnsVersion() {
		Profile profile = profile();
		ProfileSkill association = profileSkill(profile, skill(22L, "Java"), 12L, "1.00", null);
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(profileSkills.findByIdAndProfileId(12L, 8L)).thenReturn(Optional.of(association));
		simulateVersionIncrementOnAdvance(profile);

		ProfileVersionResponse result = service.delete(principal, 8L, 12L, 0L);

		assertEquals(1L, result.profileVersion());
		verify(profileSkills).delete(association);
		verify(profileSkills, never()).saveAndFlush(any());
		verify(profileVersions).advance(profile);
	}

	@Test
	void rejectsForeignAssociationAndStaleProfileVersion() {
		Profile profile = profile();
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(profileSkills.findByIdAndProfileId(12L, 8L)).thenReturn(Optional.empty());

		ApiException foreign = assertThrows(ApiException.class, () -> service.delete(principal, 8L, 12L, 0L));
		assertEquals(ErrorCode.PROFILE_SKILL_NOT_FOUND, foreign.getErrorCode());

		ApiException stale = assertThrows(ApiException.class, () -> service.create(principal, 8L,
				new ProfileSkillRequest(22L, new BigDecimal("1.00"), null, 1L)));
		assertEquals(ErrorCode.PROFILE_VERSION_CONFLICT, stale.getErrorCode());
		verify(profileVersions, never()).advance(any());
	}

	@Test
	void translatesDatabaseDuplicateAndOptimisticLockFailures() {
		Profile profile = profile();
		Skill skill = skill(22L, "Java");
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(skills.findById(22L)).thenReturn(Optional.of(skill));
		when(profileSkills.saveAndFlush(any(ProfileSkill.class))).thenAnswer(invocation -> invocation.getArgument(0));
		ConstraintViolationException violation = new ConstraintViolationException("duplicate", null,
				"uk_profile_skills_profile_skill");
		doThrow(new DataIntegrityViolationException("duplicate", violation)).when(profileSkills).saveAndFlush(any());

		ApiException duplicate = assertThrows(ApiException.class, () -> service.create(principal, 8L,
				new ProfileSkillRequest(22L, new BigDecimal("1.00"), null, 0L)));
		assertEquals(ErrorCode.PROFILE_SKILL_ALREADY_EXISTS, duplicate.getErrorCode());

		org.mockito.Mockito.reset(profileSkills, profileVersions);
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(skills.findById(22L)).thenReturn(Optional.of(skill));
		when(profileSkills.saveAndFlush(any(ProfileSkill.class))).thenAnswer(invocation -> invocation.getArgument(0));
		doThrow(new OptimisticLockException()).when(profileVersions).advance(any());

		ApiException conflict = assertThrows(ApiException.class, () -> service.create(principal, 8L,
				new ProfileSkillRequest(22L, new BigDecimal("1.00"), null, 0L)));
		assertEquals(ErrorCode.PROFILE_VERSION_CONFLICT, conflict.getErrorCode());
	}

	@Test
	void returnsProfileNotFoundForMissingActiveOwnerProfile() {
		when(profileAccess.resolve(principal, 8L)).thenThrow(new ApiException(HttpStatus.NOT_FOUND,
				ErrorCode.PROFILE_NOT_FOUND, "Profile not found"));

		ApiException exception = assertThrows(ApiException.class, () -> service.list(principal, 8L));

		assertEquals(ErrorCode.PROFILE_NOT_FOUND, exception.getErrorCode());
		verify(profileSkills, never()).findByProfileId(any());
	}

	private Profile profile() {
		return new Profile(user(), "Profile", "First", "Last", "Engineer", BigDecimal.ONE, "Personality", "Summary");
	}

	private UserAccount user() {
		return new UserAccount("user@example.com", "member", "hash", UserRole.MEMBER, UserStatus.ACTIVE);
	}

	private Skill skill(Long id, String name) {
		Skill skill = new Skill(name, category(id + 100L));
		ReflectionTestUtils.setField(skill, "id", id);
		return skill;
	}

	private SkillCategory category(Long id) {
		SkillCategory category = new SkillCategory("BACKEND", "Backend");
		ReflectionTestUtils.setField(category, "id", id);
		return category;
	}

	private ProfileSkill profileSkill(Profile profile, Skill skill, Long id, String experienceYears, LocalDate lastUsed) {
		ProfileSkill profileSkill = new ProfileSkill(profile, skill, new BigDecimal(experienceYears), lastUsed);
		ReflectionTestUtils.setField(profileSkill, "id", id);
		return profileSkill;
	}

	private void simulateVersionIncrementOnAdvance(Profile profile) {
		org.mockito.Mockito.doAnswer(invocation -> {
			long nextVersion = profile.getVersion() + 1;
			ReflectionTestUtils.setField(profile, "version", nextVersion);
			ReflectionTestUtils.setField(profile, "hasPreviewed", false);
			return nextVersion;
		}).when(profileVersions).advance(profile);
	}
}
