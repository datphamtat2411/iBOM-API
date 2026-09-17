package com.fpt.ibom.profile;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.config.SecurityConfig;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.profile.controller.MemberProfileController;
import com.fpt.ibom.profile.dto.ProfileSummaryResponse;
import com.fpt.ibom.profile.service.ProfileService;
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

@WebMvcTest(controllers = MemberProfileController.class)
@Import({MemberProfileController.class, SecurityConfig.class})
@ActiveProfiles("test")
class MemberProfileControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProfileService profileService;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@MockitoBean
	private UserAccountRepository userAccountRepository;

	@Test
	void listsMemberProfilesForManagersAndAdminsUsingSummaryContract() throws Exception {
		when(profileService.listMemberProfiles(7L)).thenReturn(List.of(
				new ProfileSummaryResponse(8L, "Default", "First", "Last", "Engineer", null)));

		for (UserRole role : List.of(UserRole.MANAGER, UserRole.ADMIN)) {
			mockMvc.perform(get("/api/members/7/profiles").with(principal(role))).andExpect(status().isOk())
					.andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data[0].id").value(8))
					.andExpect(jsonPath("$.data[0].profileName").value("Default"))
					.andExpect(jsonPath("$.data[0].userId").doesNotExist())
					.andExpect(jsonPath("$.data[0].deletedAt").doesNotExist());
		}

		verify(profileService, org.mockito.Mockito.times(2)).listMemberProfiles(7L);
	}

	@Test
	void requiresManagerOrAdminAuthorityForMemberProfileListing() throws Exception {
		mockMvc.perform(get("/api/members/7/profiles").with(principal(UserRole.MEMBER)))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/members/7/profiles")).andExpect(status().isUnauthorized());
	}

	@Test
	void mapsMissingOrNonMemberTargetsToNotFound() throws Exception {
		when(profileService.listMemberProfiles(7L)).thenThrow(new ApiException(HttpStatus.NOT_FOUND,
				ErrorCode.PROFILE_NOT_FOUND, "Profile not found"));

		mockMvc.perform(get("/api/members/7/profiles").with(principal(UserRole.MANAGER)))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("PROFILE_NOT_FOUND"));
}

	private org.springframework.test.web.servlet.request.RequestPostProcessor principal(UserRole role) {
		return authentication(new UsernamePasswordAuthenticationToken(
				new UserPrincipal(20L, "user@example.com", "user", role), null,
				List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role.name()))));
	}
}
