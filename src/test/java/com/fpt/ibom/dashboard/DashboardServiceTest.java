package com.fpt.ibom.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
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
import com.fpt.ibom.profile.dto.ProfileCompletenessResponse;
import com.fpt.ibom.profile.entity.Profile;
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
	private final DashboardService service = new DashboardService(profileAccessService, profileCompletenessService,
			profileService, profileEligibilityService);

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
		verify(profileCompletenessService).countCompleted(List.of());
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
}
