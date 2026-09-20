package com.fpt.ibom.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyLong;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.dashboard.dto.ManagerDashboardStatsResponse;
import com.fpt.ibom.dashboard.dto.MemberDashboardStatsResponse;
import com.fpt.ibom.dashboard.service.DashboardService;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.master.entity.Skill;
import com.fpt.ibom.master.entity.SkillCategory;
import com.fpt.ibom.profile.dto.ProfileCompletenessResponse;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.entity.ProfileSkill;
import com.fpt.ibom.profile.repository.ProfileSkillRepository;
import com.fpt.ibom.profile.service.ProfileAccessService;
import com.fpt.ibom.profile.service.ProfileCompletenessService;
import com.fpt.ibom.profile.service.ProfileEligibilityService;
import com.fpt.ibom.profile.service.ProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class DashboardServiceTest {

	private final ProfileAccessService profileAccessService = mock(ProfileAccessService.class);
	private final ProfileCompletenessService profileCompletenessService = mock(ProfileCompletenessService.class);
	private final ProfileService profileService = mock(ProfileService.class);
	private final ProfileEligibilityService profileEligibilityService = mock(ProfileEligibilityService.class);
	private final ProfileSkillRepository profileSkillRepository = mock(ProfileSkillRepository.class);
	private final DashboardService service = new DashboardService(profileAccessService, profileCompletenessService,
			profileService, profileEligibilityService, profileSkillRepository);

	@Test
	void resolvesTheSuppliedProfileThroughOwnerScopedAccessAndReusesCanonicalCompleteness() {
		UserPrincipal principal = principal(7L, UserRole.MANAGER);
		Profile selected = profile(8L, 7L);
		ProfileCompletenessResponse canonical = new ProfileCompletenessResponse(67, false, List.of());
		Instant latest = Instant.parse("2026-02-03T04:05:06Z");
		when(profileAccessService.resolveOwned(principal, 8L)).thenReturn(selected);
		when(profileCompletenessService.calculate(selected)).thenReturn(canonical);
		when(profileService.latestExportedAt(7L)).thenReturn(latest);

		MemberDashboardStatsResponse result = service.getStats(principal, 8L);

		assertEquals(8L, result.selectedProfile().id());
		assertSame(canonical, result.completeness());
		assertEquals(latest, result.latestExportedAt());
		verify(profileAccessService).resolveOwned(principal, 8L);
		verify(profileCompletenessService).calculate(selected);
		verify(profileService).latestExportedAt(7L);
		verifyNoMoreInteractions(profileAccessService, profileCompletenessService, profileService);
	}

	@Test
	void returnsNullWhenTheOwnerHasNoExport() {
		UserPrincipal principal = principal(7L, UserRole.MEMBER);
		Profile selected = profile(8L, 7L);
		when(profileAccessService.resolveOwned(principal, 8L)).thenReturn(selected);
		when(profileCompletenessService.calculate(selected)).thenReturn(new ProfileCompletenessResponse(0, false, List.of()));
		when(profileService.latestExportedAt(7L)).thenReturn(null);

		assertNull(service.getStats(principal, 8L).latestExportedAt());
	}

	@Test
	void doesNotCalculateOrQueryStatsWhenForeignOrDeletedProfileIsRejected() {
		UserPrincipal principal = principal(7L, UserRole.MEMBER);
		ApiException notFound = new ApiException(HttpStatus.NOT_FOUND, ErrorCode.PROFILE_NOT_FOUND, "Profile not found");
		when(profileAccessService.resolveOwned(principal, 8L)).thenThrow(notFound);

		assertThrows(ApiException.class, () -> service.getStats(principal, 8L));
		verify(profileAccessService).resolveOwned(principal, 8L);
		verifyNoMoreInteractions(profileAccessService, profileCompletenessService, profileService);
	}

	@Test
	void countsEligibleProfilesAndDelegatesCompletionToCanonicalService() {
		Profile first = profile(8L, 7L);
		Profile second = profile(9L, 7L);
		Profile third = profile(10L, 8L);
		List<Profile> eligibleProfiles = List.of(first, second, third);
		when(profileEligibilityService.findEligibleMemberProfiles()).thenReturn(eligibleProfiles);
		when(profileCompletenessService.countCompleted(eligibleProfiles)).thenReturn(2);

		ManagerDashboardStatsResponse result = service.getManagerStats();

		assertEquals(3, result.totalProfiles());
		assertEquals(2, result.completedProfiles());
		verify(profileSkillRepository).findByProfileIdIn(List.of(8L, 9L, 10L));
		verify(profileEligibilityService).findEligibleMemberProfiles();
		verify(profileCompletenessService).countCompleted(eligibleProfiles);
		verifyNoMoreInteractions(profileEligibilityService, profileCompletenessService);
	}

	@Test
	void returnsZeroCountsWhenNoEligibleProfilesExist() {
		when(profileEligibilityService.findEligibleMemberProfiles()).thenReturn(List.of());
		when(profileCompletenessService.countCompleted(List.of())).thenReturn(0);

		ManagerDashboardStatsResponse result = service.getManagerStats();

		assertEquals(0, result.totalProfiles());
		assertEquals(0, result.completedProfiles());
		assertEquals(List.of(), result.primarySkillDistribution().items());
		assertEquals(0, result.primarySkillDistribution().otherProfileCount());
		assertEquals(List.of(), result.skillCategoryDistribution().items());
		assertEquals(0, result.skillCategoryDistribution().otherProfileCount());
		verify(profileCompletenessService).countCompleted(List.of());
		verifyNoInteractions(profileSkillRepository);
	}

	@Test
	void selectsOnePrimarySkillPerProfileAndUsesOnlyThoseSkillsForAnalytics() {
		Profile first = profile(8L, 7L);
		Profile second = profile(9L, 7L);
		Profile third = profile(10L, 7L);
		Profile fourth = profile(11L, 7L);
		Profile withoutSkills = profile(12L, 7L);
		List<Profile> eligibleProfiles = List.of(first, second, third, fourth, withoutSkills);
		SkillCategory backend = category(41L, "BACKEND", "Backend");
		SkillCategory frontend = category(42L, "FRONTEND", "Frontend");
		Skill java = skill(11L, "Java", backend);
		Skill python = skill(12L, "Python", frontend);
		Skill kotlin = skill(13L, "Kotlin", frontend);
		Skill lowercaseJava = skill(14L, "java", backend);
		Skill uppercaseJava = skill(11L, "Java", backend);
		Skill go = skill(16L, "Go", null);
		Skill lowercaseGo = skill(17L, "go", backend);
		when(profileEligibilityService.findEligibleMemberProfiles()).thenReturn(eligibleProfiles);
		when(profileCompletenessService.countCompleted(eligibleProfiles)).thenReturn(3);
		when(profileSkillRepository.findByProfileIdIn(List.of(8L, 9L, 10L, 11L, 12L))).thenReturn(List.of(
			new ProfileSkill(first, python, new BigDecimal("4.00"), LocalDate.of(2025, 1, 1)),
			new ProfileSkill(first, java, new BigDecimal("5.00"), LocalDate.of(2024, 1, 1)),
			new ProfileSkill(second, java, new BigDecimal("5.00"), null),
			new ProfileSkill(second, kotlin, new BigDecimal("5.00"), LocalDate.of(2023, 1, 1)),
			new ProfileSkill(third, lowercaseJava, new BigDecimal("3.00"), LocalDate.of(2023, 1, 1)),
			new ProfileSkill(third, uppercaseJava, new BigDecimal("3.00"), LocalDate.of(2023, 1, 1)),
			new ProfileSkill(fourth, lowercaseGo, new BigDecimal("2.00"), LocalDate.of(2022, 1, 1)),
			new ProfileSkill(fourth, go, new BigDecimal("2.00"), LocalDate.of(2022, 1, 1))));

		ManagerDashboardStatsResponse result = service.getManagerStats();

		assertEquals(5, result.totalProfiles());
		assertEquals(3, result.completedProfiles());
		assertEquals(List.of("Java", "Go", "Kotlin"), result.primarySkillDistribution().items().stream()
				.map(ManagerDashboardStatsResponse.PrimarySkillItem::skillName).toList());
		assertEquals(List.of(2L, 1L, 1L), result.primarySkillDistribution().items().stream()
				.map(ManagerDashboardStatsResponse.PrimarySkillItem::profileCount).toList());
		assertEquals(0, result.primarySkillDistribution().otherProfileCount());
		assertEquals(List.of("Backend", "Frontend", "Uncategorized"), result.skillCategoryDistribution().items().stream()
				.map(ManagerDashboardStatsResponse.SkillCategoryItem::categoryName).toList());
		assertEquals(List.of(2L, 1L, 1L), result.skillCategoryDistribution().items().stream()
				.map(ManagerDashboardStatsResponse.SkillCategoryItem::profileCount).toList());
		assertEquals(List.of(50, 25, 25), result.skillCategoryDistribution().items().stream()
				.map(ManagerDashboardStatsResponse.SkillCategoryItem::percentage).toList());
		assertEquals(0, result.skillCategoryDistribution().otherProfileCount());
		verify(profileSkillRepository).findByProfileIdIn(List.of(8L, 9L, 10L, 11L, 12L));
		verify(profileSkillRepository, never()).findByProfileId(anyLong());
	}

	@Test
	void ignoresMalformedSkillsAndExcludesProfilesWithoutAValidSkill() {
		Profile valid = profile(20L, 7L);
		Profile mixed = profile(21L, 7L);
		Profile withoutValidSkill = profile(22L, 7L);
		Profile withUnusableCategory = profile(23L, 7L);
		List<Profile> eligibleProfiles = List.of(valid, mixed, withoutValidSkill, withUnusableCategory);
		SkillCategory backend = category(51L, "BACKEND", "Backend");
		SkillCategory frontend = category(52L, "FRONTEND", "Frontend");
		Skill java = skill(21L, "Java", backend);
		Skill python = skill(22L, "Python", frontend);
		Skill go = skill(23L, "Go", category(53L, null, null));
		Skill malformed = skill(null, "Legacy", backend);
		when(profileEligibilityService.findEligibleMemberProfiles()).thenReturn(eligibleProfiles);
		when(profileCompletenessService.countCompleted(eligibleProfiles)).thenReturn(2);
		when(profileSkillRepository.findByProfileIdIn(List.of(20L, 21L, 22L, 23L))).thenReturn(List.of(
				new ProfileSkill(valid, java, new BigDecimal("5.00"), LocalDate.of(2025, 1, 1)),
				new ProfileSkill(mixed, null, new BigDecimal("9.00"), LocalDate.of(2026, 1, 1)),
				new ProfileSkill(mixed, python, new BigDecimal("4.00"), LocalDate.of(2024, 1, 1)),
				new ProfileSkill(withoutValidSkill, null, BigDecimal.ONE, LocalDate.of(2024, 1, 1)),
				new ProfileSkill(withoutValidSkill, malformed, new BigDecimal("8.00"), LocalDate.of(2026, 1, 1)),
				new ProfileSkill(withUnusableCategory, go, new BigDecimal("3.00"), null)));

		ManagerDashboardStatsResponse result = service.getManagerStats();

		assertEquals(4, result.totalProfiles());
		assertEquals(2, result.completedProfiles());
		assertEquals(List.of("Go", "Java", "Python"), result.primarySkillDistribution().items().stream()
				.map(ManagerDashboardStatsResponse.PrimarySkillItem::skillName).toList());
		assertEquals(List.of(1L, 1L, 1L), result.primarySkillDistribution().items().stream()
				.map(ManagerDashboardStatsResponse.PrimarySkillItem::profileCount).toList());
		assertEquals(0, result.primarySkillDistribution().otherProfileCount());
		assertEquals(List.of("Backend", "Frontend", "Uncategorized"), result.skillCategoryDistribution().items().stream()
				.map(ManagerDashboardStatsResponse.SkillCategoryItem::categoryName).toList());
		assertEquals(List.of(1L, 1L, 1L), result.skillCategoryDistribution().items().stream()
				.map(ManagerDashboardStatsResponse.SkillCategoryItem::profileCount).toList());
		assertEquals(List.of(33, 33, 33), result.skillCategoryDistribution().items().stream()
				.map(ManagerDashboardStatsResponse.SkillCategoryItem::percentage).toList());
		assertEquals(0, result.skillCategoryDistribution().otherProfileCount());
	}

	@Test
	void keepsOnlySevenItemsAndSumsOmittedProfilesForBothDistributions() {
		List<Profile> eligibleProfiles = java.util.stream.IntStream.rangeClosed(1, 8)
				.mapToObj(index -> profile((long) index, 7L)).toList();
		List<ProfileSkill> profileSkills = java.util.stream.IntStream.rangeClosed(1, 8).mapToObj(index -> {
			SkillCategory category = category(100L + index, "CAT_" + index, "Category " + index);
			Skill skill = skill(200L + index, "Skill " + index, category);
			return new ProfileSkill(eligibleProfiles.get(index - 1), skill, BigDecimal.ONE, LocalDate.of(2024, 1, 1));
		}).toList();
		when(profileEligibilityService.findEligibleMemberProfiles()).thenReturn(eligibleProfiles);
		when(profileCompletenessService.countCompleted(eligibleProfiles)).thenReturn(0);
		when(profileSkillRepository.findByProfileIdIn(java.util.stream.LongStream.rangeClosed(1, 8).boxed().toList()))
				.thenReturn(profileSkills);

		ManagerDashboardStatsResponse result = service.getManagerStats();

		assertEquals(List.of("Skill 1", "Skill 2", "Skill 3", "Skill 4", "Skill 5", "Skill 6", "Skill 7"),
				result.primarySkillDistribution().items().stream()
						.map(ManagerDashboardStatsResponse.PrimarySkillItem::skillName).toList());
		assertEquals(1, result.primarySkillDistribution().otherProfileCount());
		assertEquals(List.of("Category 1", "Category 2", "Category 3", "Category 4", "Category 5", "Category 6",
				"Category 7"), result.skillCategoryDistribution().items().stream()
						.map(ManagerDashboardStatsResponse.SkillCategoryItem::categoryName).toList());
		assertEquals(List.of(13, 13, 13, 13, 13, 13, 13), result.skillCategoryDistribution().items().stream()
				.map(ManagerDashboardStatsResponse.SkillCategoryItem::percentage).toList());
		assertEquals(1, result.skillCategoryDistribution().otherProfileCount());
	}

	private UserPrincipal principal(Long userId, UserRole role) {
		return new UserPrincipal(userId, "user@example.com", "user", role);
	}

	private Profile profile(Long profileId, Long userId) {
		UserAccount user = new UserAccount("user" + userId + "@example.com", "user" + userId, "hash",
				UserRole.MEMBER, UserStatus.ACTIVE);
		ReflectionTestUtils.setField(user, "id", userId);
		Profile profile = new Profile(user, "Selected", "First", "Last", "Engineer", BigDecimal.ONE,
				"Personality", "Summary");
		ReflectionTestUtils.setField(profile, "id", profileId);
		return profile;
	}

	private SkillCategory category(Long categoryId, String code, String name) {
		SkillCategory category = new SkillCategory(code, name);
		ReflectionTestUtils.setField(category, "id", categoryId);
		return category;
	}

	private Skill skill(Long skillId, String name, SkillCategory category) {
		Skill skill = new Skill(name, category);
		ReflectionTestUtils.setField(skill, "id", skillId);
		return skill;
	}
}
