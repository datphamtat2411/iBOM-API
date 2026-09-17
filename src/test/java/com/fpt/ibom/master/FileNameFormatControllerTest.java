package com.fpt.ibom.master;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
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
import com.fpt.ibom.master.controller.FileNameFormatController;
import com.fpt.ibom.master.dto.FileNameFormatRequest;
import com.fpt.ibom.master.dto.FileNameFormatResponse;
import com.fpt.ibom.master.service.FileNameFormatService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = FileNameFormatController.class)
@Import({ FileNameFormatController.class, SecurityConfig.class })
@ActiveProfiles("test")
class FileNameFormatControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private FileNameFormatService fileNameFormatService;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@MockitoBean
	private UserAccountRepository userAccountRepository;

	@ParameterizedTest
	@EnumSource(UserRole.class)
	void allowsEveryAuthenticatedRoleToRead(UserRole role) throws Exception {
		when(fileNameFormatService.list(0, 10)).thenReturn(page());

		mockMvc.perform(get("/api/master/file-name-formats").with(principal(role)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(200))
				.andExpect(jsonPath("$.data.content[0].id").value(12))
				.andExpect(jsonPath("$.data.content[0].name").value("Custom"))
				.andExpect(jsonPath("$.data.content[0].pattern").value("{LastName}_{Date}"))
				.andExpect(jsonPath("$.data.content[0].isDefault").value(false));
	}

	@Test
	void appliesDefaultPagination() throws Exception {
		when(fileNameFormatService.list(0, 10)).thenReturn(new PageResponse<>(List.of(), 0, 10, 0, 0));

		mockMvc.perform(get("/api/master/file-name-formats").with(principal(UserRole.MEMBER)))
				.andExpect(status().isOk());

		verify(fileNameFormatService).list(0, 10);
	}

	@Test
	void memberCannotMutate() throws Exception {
		mockMvc.perform(post("/api/master/file-name-formats").with(principal(UserRole.MEMBER))
				.contentType(MediaType.APPLICATION_JSON).content(requestJson()))
				.andExpect(status().isForbidden());
		mockMvc.perform(put("/api/master/file-name-formats/12").with(principal(UserRole.MEMBER))
				.contentType(MediaType.APPLICATION_JSON).content(requestJson()))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/master/file-name-formats/12").with(principal(UserRole.MEMBER)))
				.andExpect(status().isForbidden());
	}

	@ParameterizedTest
	@EnumSource(value = UserRole.class, names = { "MANAGER", "ADMIN" })
	void managerAndAdminCanMutate(UserRole role) throws Exception {
		FileNameFormatResponse response = new FileNameFormatResponse(12L, "Custom", "{Date}", false,
				Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-02T00:00:00Z"));
		when(fileNameFormatService.create(new FileNameFormatRequest("Custom", "{Date}"))).thenReturn(response);
		when(fileNameFormatService.update(12L, new FileNameFormatRequest("Custom", "{Date}"))).thenReturn(response);

		mockMvc.perform(post("/api/master/file-name-formats").with(principal(role))
				.contentType(MediaType.APPLICATION_JSON).content(requestJson()))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").value(12));
		mockMvc.perform(put("/api/master/file-name-formats/12").with(principal(role))
				.contentType(MediaType.APPLICATION_JSON).content(requestJson()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Custom"));
		mockMvc.perform(delete("/api/master/file-name-formats/12").with(principal(role)))
				.andExpect(status().isOk());
	}

	@Test
	void validatesRequestAndRequiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/master/file-name-formats").with(principal(UserRole.MANAGER))
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
		mockMvc.perform(get("/api/master/file-name-formats"))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode").value("REQUEST_FAILED"));
	}

	private PageResponse<FileNameFormatResponse> page() {
		return new PageResponse<>(List.of(new FileNameFormatResponse(12L, "Custom", "{LastName}_{Date}", false,
				Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-02T00:00:00Z"))), 0, 10, 1, 1);
	}

	private String requestJson() {
		return "{\"name\":\"Custom\",\"pattern\":\"{Date}\"}";
	}

	private org.springframework.test.web.servlet.request.RequestPostProcessor principal(UserRole role) {
		return authentication(new UsernamePasswordAuthenticationToken(
				new UserPrincipal(7L, "user@example.com", "user", role), null,
				List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
	}
}
