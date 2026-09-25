package com.fpt.ibom.dashboard;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.config.SecurityConfig;
import com.fpt.ibom.dashboard.controller.DashboardController;
import com.fpt.ibom.dashboard.dto.ManagerDashboardStatsResponse;
import com.fpt.ibom.dashboard.dto.MemberDashboardStatsResponse;
import com.fpt.ibom.dashboard.service.DashboardService;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.profile.dto.ProfileCompletenessResponse;
import com.fpt.ibom.profile.dto.ProfileCompletenessSectionResponse;
import com.fpt.ibom.profile.dto.ProfileSummaryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DashboardController.class)
@Import({DashboardController.class, SecurityConfig.class})
@ActiveProfiles("test")
class DashboardControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DashboardService dashboardService;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@MockitoBean
	private UserAccountRepository userAccountRepository;

	@Test
	void returnsDashboardStatsForAllAuthenticatedRolesWithExplicitProfileId() throws Exception {
		Instant latest = Instant.parse("2026-02-03T04:05:06Z");
		when(dashboardService.getStats(any(UserPrincipal.class), eq(8L)))
				.thenReturn(response(latest));

		for (UserRole role : List.of(UserRole.MEMBER, UserRole.MANAGER, UserRole.ADMIN)) {
			mockMvc.perform(get("/api/dashboard/my-stats").param("profileId", "8").with(principal(role)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.code").value(200))
					.andExpect(jsonPath("$.data.selectedProfile.id").value(8))
					.andExpect(jsonPath("$.data.selectedProfile.profileName").value("Selected"))
					.andExpect(jsonPath("$.data.completeness.percentage").value(67.0))
					.andExpect(jsonPath("$.data.completeness.sections[0].key").value("aboutMe"))
					.andExpect(jsonPath("$.data.completeness.sections[0].validFieldCount").value(4))
					.andExpect(jsonPath("$.data.latestExportedAt").value("2026-02-03T04:05:06Z"));
		}

		verify(dashboardService, org.mockito.Mockito.times(3)).getStats(any(UserPrincipal.class), eq(8L));
	}

	@Test
	void rejectsMissingOrInvalidProfileIdBeforeCallingDashboardService() throws Exception {
		mockMvc.perform(get("/api/dashboard/my-stats").with(principal(UserRole.MEMBER)))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
		mockMvc.perform(get("/api/dashboard/my-stats").param("profileId", "not-a-number")
				.with(principal(UserRole.MEMBER)))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
		verify(dashboardService, never()).getStats(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	@Test
	void requiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/dashboard/my-stats").param("profileId", "8"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void mapsMissingSelectedProfileToProfileNotFound() throws Exception {
		when(dashboardService.getStats(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(8L)))
				.thenThrow(new ApiException(HttpStatus.NOT_FOUND, ErrorCode.PROFILE_NOT_FOUND, "Profile not found"));

		mockMvc.perform(get("/api/dashboard/my-stats").param("profileId", "8").with(principal(UserRole.MEMBER)))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("PROFILE_NOT_FOUND"));
	}

	@Test
	void requiresAuthenticationForManagerStats() throws Exception {
		mockMvc.perform(get("/api/dashboard/manager-stats"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void rejectsMemberForManagerStats() throws Exception {
		mockMvc.perform(get("/api/dashboard/manager-stats").with(principal(UserRole.MEMBER)))
				.andExpect(status().isForbidden());
		verify(dashboardService, never()).getManagerStats();
	}

	@Test
	void returnsManagerStatsForManagerAndAdmin() throws Exception {
		when(dashboardService.getManagerStats()).thenReturn(new ManagerDashboardStatsResponse(3, 2,
				new ManagerDashboardStatsResponse.PrimarySkillDistribution(
						List.of(new ManagerDashboardStatsResponse.PrimarySkillItem(1L, "Java", 2L)), 1L),
				new ManagerDashboardStatsResponse.SkillCategoryDistribution(
						List.of(new ManagerDashboardStatsResponse.SkillCategoryItem(4L, "BACKEND", "Backend", 2L, 67)), 1L)));

		for (UserRole role : List.of(UserRole.MANAGER, UserRole.ADMIN)) {
			mockMvc.perform(get("/api/dashboard/manager-stats").with(principal(role)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.code").value(200))
					.andExpect(jsonPath("$.data.totalProfiles").value(3))
					.andExpect(jsonPath("$.data.completedProfiles").value(2))
					.andExpect(jsonPath("$.data.primarySkillDistribution.items[0].skillId").value(1))
					.andExpect(jsonPath("$.data.primarySkillDistribution.items[0].skillName").value("Java"))
					.andExpect(jsonPath("$.data.primarySkillDistribution.items[0].profileCount").value(2))
					.andExpect(jsonPath("$.data.primarySkillDistribution.otherProfileCount").value(1))
					.andExpect(jsonPath("$.data.skillCategoryDistribution.items[0].categoryId").value(4))
					.andExpect(jsonPath("$.data.skillCategoryDistribution.items[0].categoryCode").value("BACKEND"))
					.andExpect(jsonPath("$.data.skillCategoryDistribution.items[0].categoryName").value("Backend"))
					.andExpect(jsonPath("$.data.skillCategoryDistribution.items[0].profileCount").value(2))
					.andExpect(jsonPath("$.data.skillCategoryDistribution.items[0].percentage").value(67))
					.andExpect(jsonPath("$.data.skillCategoryDistribution.otherProfileCount").value(1));
		}

		verify(dashboardService, org.mockito.Mockito.times(2)).getManagerStats();
	}

	private MemberDashboardStatsResponse response(Instant latest) {
		return new MemberDashboardStatsResponse(
				new ProfileSummaryResponse(8L, "Selected", "First", "Last", "Engineer", null),
				new ProfileCompletenessResponse(new BigDecimal("67.00"), false, List.of(
						new ProfileCompletenessSectionResponse("aboutMe", 20, false, 4, 6, null))), latest);
	}

	private org.springframework.test.web.servlet.request.RequestPostProcessor principal(UserRole role) {
		return authentication(new UsernamePasswordAuthenticationToken(
				new UserPrincipal(20L, "user@example.com", "user", role), null,
				List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role.name()))));
	}
}
