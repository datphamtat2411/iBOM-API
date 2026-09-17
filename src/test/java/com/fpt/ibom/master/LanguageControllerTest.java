package com.fpt.ibom.master;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.common.PageResponse;
import com.fpt.ibom.config.SecurityConfig;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.master.controller.LanguageController;
import com.fpt.ibom.master.dto.LanguageRequest;
import com.fpt.ibom.master.dto.LanguageResponse;
import com.fpt.ibom.master.service.LanguageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = LanguageController.class)
@Import({ LanguageController.class, SecurityConfig.class })
@ActiveProfiles("test")
class LanguageControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private LanguageService languageService;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@MockitoBean
	private UserAccountRepository userAccountRepository;

	@ParameterizedTest
	@EnumSource(UserRole.class)
	void allowsEveryExistingAuthenticatedRoleToReadLanguages(UserRole role) throws Exception {
		when(languageService.list(0, 10, null)).thenReturn(page());

		mockMvc.perform(get("/api/master/languages").with(principal(role)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(200))
				.andExpect(jsonPath("$.data.content[0].id").value(12))
				.andExpect(jsonPath("$.data.content[0].name").value("English"))
				.andExpect(jsonPath("$.data.content[0].createdAt").value("2026-01-01T00:00:00Z"))
				.andExpect(jsonPath("$.data.content[0].updatedAt").value("2026-01-02T00:00:00Z"))
				.andExpect(jsonPath("$.data.page").value(0))
				.andExpect(jsonPath("$.data.size").value(10))
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.totalPages").value(1));
	}

	@Test
	void appliesDefaultPaginationAndForwardsExplicitQueryParameters() throws Exception {
		when(languageService.list(0, 10, null)).thenReturn(new PageResponse<>(List.of(), 0, 10, 0, 0));
		mockMvc.perform(get("/api/master/languages").with(principal(UserRole.MEMBER)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.content").isEmpty());
		verify(languageService).list(0, 10, null);

		when(languageService.list(2, 1, "  jap  ")).thenReturn(page());
		mockMvc.perform(get("/api/master/languages").param("page", "2").param("size", "1")
				.param("search", "  jap  ").with(principal(UserRole.MEMBER)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].name").value("English"));
		verify(languageService).list(2, 1, "  jap  ");
	}

	@Test
	void requiresAuthenticationForLanguageReads() throws Exception {
		mockMvc.perform(get("/api/master/languages"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(401))
				.andExpect(jsonPath("$.errorCode").value("REQUEST_FAILED"))
				.andExpect(jsonPath("$.data").doesNotExist());
	}

	@Test
	void exposesCreateUpdateAndDeleteWithCommonResponses() throws Exception {
		when(languageService.create(new LanguageRequest(" English "))).thenReturn(response());
		when(languageService.update(12L, new LanguageRequest(" English "))).thenReturn(response());
		doNothing().when(languageService).delete(12L);

		mockMvc.perform(post("/api/master/languages").with(principal(UserRole.MANAGER)).contentType(APPLICATION_JSON)
				.content("{\"name\":\" English \"}"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.code").value(201))
				.andExpect(jsonPath("$.data.name").value("English"));
		mockMvc.perform(put("/api/master/languages/12").with(principal(UserRole.ADMIN)).contentType(APPLICATION_JSON)
				.content("{\"name\":\" English \"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
				.andExpect(jsonPath("$.data.name").value("English"));
		mockMvc.perform(delete("/api/master/languages/12").with(principal(UserRole.MANAGER)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
				.andExpect(jsonPath("$.data").doesNotExist());
	}

	@ParameterizedTest
	@org.junit.jupiter.params.provider.MethodSource("mutationRoutes")
	void forbidsMemberLanguageMutations(String method, String uri) throws Exception {
		var builder = switch (method) {
			case "POST" -> post(uri).contentType(APPLICATION_JSON).content("{\"name\":\"English\"}");
			case "PUT" -> put(uri).contentType(APPLICATION_JSON).content("{\"name\":\"English\"}");
			default -> delete(uri);
		};
		mockMvc.perform(builder.with(principal(UserRole.MEMBER)))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("REQUEST_FAILED"));
	}

	private static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> mutationRoutes() {
		return java.util.stream.Stream.of(
				org.junit.jupiter.params.provider.Arguments.of("POST", "/api/master/languages"),
				org.junit.jupiter.params.provider.Arguments.of("PUT", "/api/master/languages/12"),
				org.junit.jupiter.params.provider.Arguments.of("DELETE", "/api/master/languages/12"));
	}

	@Test
	void mapsValidationAndBusinessErrors() throws Exception {
		mockMvc.perform(post("/api/master/languages").with(principal(UserRole.ADMIN)).contentType(APPLICATION_JSON)
				.content("{\"name\":\"  \"}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
		when(languageService.create(new LanguageRequest("English"))).thenThrow(
				new ApiException(org.springframework.http.HttpStatus.CONFLICT, ErrorCode.LANGUAGE_ALREADY_EXISTS,
						"Language already exists"));
		mockMvc.perform(post("/api/master/languages").with(principal(UserRole.ADMIN)).contentType(APPLICATION_JSON)
				.content("{\"name\":\"English\"}"))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("LANGUAGE_ALREADY_EXISTS"));
	}

	private PageResponse<LanguageResponse> page() {
		return new PageResponse<>(List.of(new LanguageResponse(12L, "English",
				Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-02T00:00:00Z"))), 0, 10, 1, 1);
	}

	private LanguageResponse response() {
		return new LanguageResponse(12L, "English", Instant.parse("2026-01-01T00:00:00Z"),
				Instant.parse("2026-01-02T00:00:00Z"));
	}

	private org.springframework.test.web.servlet.request.RequestPostProcessor principal(UserRole role) {
		return authentication(new UsernamePasswordAuthenticationToken(
				new UserPrincipal(7L, "user@example.com", "user", role), null,
				List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
	}
}
