package com.fpt.ibom.profile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.config.SecurityConfig;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.profile.controller.ProfileLanguageController;
import com.fpt.ibom.profile.dto.ProfileLanguageMutationResponse;
import com.fpt.ibom.profile.dto.ProfileLanguageResponse;
import com.fpt.ibom.profile.dto.ProfileVersionResponse;
import com.fpt.ibom.profile.entity.LanguageLevel;
import com.fpt.ibom.profile.service.ProfileLanguageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ProfileLanguageController.class)
@Import({ ProfileLanguageController.class, SecurityConfig.class })
@ActiveProfiles("test")
class ProfileLanguageControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProfileLanguageService profileLanguageService;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@MockitoBean
	private UserAccountRepository userAccountRepository;

	private final UserPrincipal principal = new UserPrincipal(7L, "user@example.com", "member", UserRole.MEMBER);

	@Test
	void mapsAllProfileLanguageRoutesAndResponseWrappers() throws Exception {
		ProfileLanguageResponse language = new ProfileLanguageResponse(12L, 22L, "English", LanguageLevel.NATIVE);
		when(profileLanguageService.list(principal, 8L)).thenReturn(List.of(language));
		when(profileLanguageService.create(eq(principal), eq(8L), any()))
				.thenReturn(new ProfileLanguageMutationResponse(language, 1L));
		when(profileLanguageService.update(eq(principal), eq(8L), eq(12L), any()))
				.thenReturn(new ProfileLanguageMutationResponse(language, 2L));
		when(profileLanguageService.delete(principal, 8L, 12L, 2L)).thenReturn(new ProfileVersionResponse(3L));

		mockMvc.perform(get("/api/profiles/8/languages").with(principal())).andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data[0].profileLanguageId").value(12))
				.andExpect(jsonPath("$.data[0].languageId").value(22))
				.andExpect(jsonPath("$.data[0].languageName").value("English"))
				.andExpect(jsonPath("$.data[0].level").value("NATIVE"));
		mockMvc.perform(post("/api/profiles/8/languages").with(principal()).contentType(MediaType.APPLICATION_JSON)
				.content(requestJson(0))).andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.profileLanguage.profileLanguageId").value(12))
				.andExpect(jsonPath("$.data.profileVersion").value(1));
		mockMvc.perform(put("/api/profiles/8/languages/12").with(principal())
				.contentType(MediaType.APPLICATION_JSON).content(requestJson(1))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.profileVersion").value(2));
		mockMvc.perform(delete("/api/profiles/8/languages/12").with(principal())
				.contentType(MediaType.APPLICATION_JSON).content("{\"version\":2}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.profileVersion").value(3));

		verify(profileLanguageService).delete(principal, 8L, 12L, 2L);
	}

	@Test
	void requiresAuthenticationAndMutationFields() throws Exception {
		mockMvc.perform(get("/api/profiles/8/languages")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/profiles/8/languages").with(principal()).contentType(MediaType.APPLICATION_JSON)
				.content("{}")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
		mockMvc.perform(delete("/api/profiles/8/languages/12").with(principal())
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
	}

	@Test
	void mapsProfileLanguageBusinessErrors() throws Exception {
		doThrow(new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.PROFILE_LANGUAGE_INVALID_LEVEL, "Invalid level"))
				.when(profileLanguageService).create(eq(principal), eq(8L), any());

		mockMvc.perform(post("/api/profiles/8/languages").with(principal()).contentType(MediaType.APPLICATION_JSON)
				.content(requestJson(0))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("PROFILE_LANGUAGE_INVALID_LEVEL"));
	}

	@Test
	void rejectsMissingAndUnsupportedLevelAtRequestValidationBoundary() throws Exception {
		mockMvc.perform(post("/api/profiles/8/languages").with(principal()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"languageId\":22,\"level\":\"\",\"version\":0}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
		mockMvc.perform(post("/api/profiles/8/languages").with(principal()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"languageId\":0,\"level\":\"NATIVE\",\"version\":-1}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
	}

	private org.springframework.test.web.servlet.request.RequestPostProcessor principal() {
		return authentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
	}

	private String requestJson(long version) {
		return "{\"languageId\":22,\"level\":\" native \",\"version\":" + version + "}";
	}
}
