package com.fpt.ibom.profile;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.config.SecurityConfig;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.profile.controller.ProfileController;
import com.fpt.ibom.profile.dto.ProfileDetailResponse;
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

@WebMvcTest(controllers = ProfileController.class)
@Import({ProfileController.class, SecurityConfig.class})
@ActiveProfiles("test")
class ProfileControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProfileService profileService;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@MockitoBean
	private UserAccountRepository userAccountRepository;

	@Test
	void requiresAuthenticationForProfileReads() throws Exception {
		mockMvc.perform(get("/api/profiles/me")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/profiles/8")).andExpect(status().isUnauthorized());
	}

	@Test
	void returnsProfileListWithoutOwnershipOrDeletionFields() throws Exception {
		when(profileService.list(7L)).thenReturn(List.of(new ProfileSummaryResponse(8L, "Default", "Full Name",
				"Engineer", null)));

		mockMvc.perform(get("/api/profiles/me").with(principal())).andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data[0].id").value(8))
				.andExpect(jsonPath("$.data[0].profileName").value("Default"))
				.andExpect(jsonPath("$.data[0].userId").doesNotExist())
				.andExpect(jsonPath("$.data[0].deletedAt").doesNotExist());
	}

	@Test
	void returnsProfileDetailAndNotFoundContract() throws Exception {
		when(profileService.get(7L, 8L)).thenReturn(new ProfileDetailResponse(8L, "Default", "Full Name", "Engineer",
				"user@example.com", "0123456789", "Address", null, false, 3L, null, null));

		mockMvc.perform(get("/api/profiles/8").with(principal())).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.version").value(3)).andExpect(jsonPath("$.data.userId").doesNotExist())
				.andExpect(jsonPath("$.data.deletedAt").doesNotExist());

		when(profileService.get(7L, 9L)).thenThrow(new ApiException(HttpStatus.NOT_FOUND, ErrorCode.PROFILE_NOT_FOUND,
				"Profile not found"));
		mockMvc.perform(get("/api/profiles/9").with(principal())).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("PROFILE_NOT_FOUND"));
	}

	private org.springframework.test.web.servlet.request.RequestPostProcessor principal() {
		return authentication(new UsernamePasswordAuthenticationToken(
				new UserPrincipal(7L, "user@example.com", "member", UserRole.MEMBER), null, List.of()));
	}
}
