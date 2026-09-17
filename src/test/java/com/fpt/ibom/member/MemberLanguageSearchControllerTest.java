package com.fpt.ibom.member;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.common.PageResponse;
import com.fpt.ibom.config.SecurityConfig;
import com.fpt.ibom.member.controller.MemberLanguageSearchController;
import com.fpt.ibom.member.dto.MemberLanguageSearchResponse;
import com.fpt.ibom.member.dto.MemberLanguageSearchProfileResponse;
import com.fpt.ibom.profile.dto.ProfileLanguageResponse;
import com.fpt.ibom.profile.entity.LanguageLevel;
import com.fpt.ibom.member.service.MemberLanguageSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = MemberLanguageSearchController.class)
@Import({ MemberLanguageSearchController.class, SecurityConfig.class })
@ActiveProfiles("test")
class MemberLanguageSearchControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private MemberLanguageSearchService service;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@MockitoBean
	private UserAccountRepository userAccountRepository;

	@Test
	void mapsRepeatedPairsDefaultsStatusAndResponseStructure() throws Exception {
		when(service.search(eq(List.of(1L, 2L)), eq(List.of("ADVANCED", "NATIVE")), eq(0), eq(10), eq(null)))
				.thenReturn(page());

		mockMvc.perform(get("/api/members/search-by-language").param("languageIds", "1", "2")
				.param("levels", "ADVANCED", "NATIVE").with(principal(UserRole.MANAGER)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
				.andExpect(jsonPath("$.data.content[0].id").value(12))
				.andExpect(jsonPath("$.data.content[0].matchingProfiles").isArray())
				.andExpect(jsonPath("$.data.content[0].matchingProfiles[0].matchingLanguages").isArray())
				.andExpect(jsonPath("$.data.content[0].fullName").doesNotExist());
		verify(service).search(List.of(1L, 2L), List.of("ADVANCED", "NATIVE"), 0, 10, null);

		when(service.search(eq(List.of(1L)), eq(List.of("NATIVE")), eq(2), eq(1), eq(UserStatus.INACTIVE)))
				.thenReturn(page());
		mockMvc.perform(get("/api/members/search-by-language").param("languageIds", "1").param("levels", "NATIVE")
				.param("status", "INACTIVE").param("page", "2").param("size", "1")
				.with(principal(UserRole.ADMIN))).andExpect(status().isOk());
		verify(service).search(List.of(1L), List.of("NATIVE"), 2, 1, UserStatus.INACTIVE);
	}

	@Test
	void requiresAuthenticationAndManagerOrAdminRole() throws Exception {
		mockMvc.perform(get("/api/members/search-by-language").param("languageIds", "1").param("levels", "NATIVE"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/members/search-by-language").param("languageIds", "1").param("levels", "NATIVE")
				.with(principal(UserRole.MEMBER))).andExpect(status().isForbidden());
	}

	@Test
	void rejectsInvalidStatusAtControllerBoundary() throws Exception {
		mockMvc.perform(get("/api/members/search-by-language").param("languageIds", "1").param("levels", "NATIVE")
				.param("status", "UNKNOWN").with(principal(UserRole.MANAGER))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
	}

	private PageResponse<MemberLanguageSearchResponse> page() {
		ProfileLanguageResponse language = new ProfileLanguageResponse(19L, 1L, "English", LanguageLevel.ADVANCED);
		MemberLanguageSearchProfileResponse profile = new MemberLanguageSearchProfileResponse(18L, "Primary", "First",
				"Last", "Engineer", Instant.parse("2026-01-04T00:00:00Z"), List.of(language));
		MemberLanguageSearchResponse member = new MemberLanguageSearchResponse(12L, "Alice", "alice@example.com",
				UserStatus.ACTIVE, 1L, Instant.parse("2026-01-04T00:00:00Z"), List.of(profile));
		return new PageResponse<>(List.of(member), 0, 10, 1, 1);
	}

	private org.springframework.test.web.servlet.request.RequestPostProcessor principal(UserRole role) {
		return authentication(new UsernamePasswordAuthenticationToken(
				new UserPrincipal(7L, "user@example.com", "user", role), null,
				List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
	}
}
