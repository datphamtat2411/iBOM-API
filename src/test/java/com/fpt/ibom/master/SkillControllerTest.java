package com.fpt.ibom.master;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
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
import com.fpt.ibom.master.controller.SkillController;
import com.fpt.ibom.master.dto.SkillRequest;
import com.fpt.ibom.master.dto.SkillResponse;
import com.fpt.ibom.master.service.SkillService;
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

@WebMvcTest(controllers = SkillController.class)
@Import({ SkillController.class, SecurityConfig.class })
@ActiveProfiles("test")
class SkillControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SkillService skillService;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@MockitoBean
	private UserAccountRepository userAccountRepository;

	@ParameterizedTest
	@EnumSource(UserRole.class)
	void allowsEveryExistingAuthenticatedRoleToReadSkills(UserRole role) throws Exception {
		when(skillService.list(0, 10, null)).thenReturn(page());

		mockMvc.perform(get("/api/master/skills").with(principal(role)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(200))
				.andExpect(jsonPath("$.data.content[0].id").value(12))
				.andExpect(jsonPath("$.data.content[0].name").value("Java"))
				.andExpect(jsonPath("$.data.content[0].categoryId").value(4))
				.andExpect(jsonPath("$.data.content[0].categoryCode").value("BACKEND"))
				.andExpect(jsonPath("$.data.content[0].categoryName").value("Backend"))
				.andExpect(jsonPath("$.data.content[0].createdAt").value("2026-01-01T00:00:00Z"))
				.andExpect(jsonPath("$.data.content[0].updatedAt").value("2026-01-02T00:00:00Z"))
				.andExpect(jsonPath("$.data.page").value(0))
				.andExpect(jsonPath("$.data.size").value(10))
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.totalPages").value(1));
	}

	@Test
	void appliesDefaultPaginationAndForwardsExplicitQueryParameters() throws Exception {
		when(skillService.list(0, 10, null)).thenReturn(new PageResponse<>(List.of(), 0, 10, 0, 0));
		mockMvc.perform(get("/api/master/skills").with(principal(UserRole.MEMBER)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.content").isEmpty());
		verify(skillService).list(0, 10, null);

		when(skillService.list(2, 1, "  jav  ")).thenReturn(page());
		mockMvc.perform(get("/api/master/skills").param("page", "2").param("size", "1")
				.param("search", "  jav  ").with(principal(UserRole.MEMBER)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].name").value("Java"));
		verify(skillService).list(2, 1, "  jav  ");
	}

	@Test
	void requiresAuthenticationForSkillReads() throws Exception {
		mockMvc.perform(get("/api/master/skills"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(401))
				.andExpect(jsonPath("$.errorCode").value("REQUEST_FAILED"))
				.andExpect(jsonPath("$.data").doesNotExist());
	}

	@Test
	void managerCanCreateUpdateAndDeleteSkills() throws Exception {
		when(skillService.create(new SkillRequest("Java", 4L))).thenReturn(page().content().get(0));
		when(skillService.update(12L, new SkillRequest("Java", 4L))).thenReturn(page().content().get(0));

		mockMvc.perform(post("/api/master/skills").with(principal(UserRole.MANAGER)).contentType(APPLICATION_JSON)
				.content("{\"name\":\"  Java  \",\"categoryId\":4}"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.code").value(201))
				.andExpect(jsonPath("$.data.name").value("Java"));
		mockMvc.perform(put("/api/master/skills/12").with(principal(UserRole.MANAGER)).contentType(APPLICATION_JSON)
				.content("{\"name\":\"Java\",\"categoryId\":4}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
		mockMvc.perform(delete("/api/master/skills/12").with(principal(UserRole.MANAGER)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
				.andExpect(jsonPath("$.data").doesNotExist());
		verify(skillService).create(new SkillRequest("Java", 4L));
		verify(skillService).update(12L, new SkillRequest("Java", 4L));
		verify(skillService).delete(12L);
	}

	@Test
	void adminCanMutateSkills() throws Exception {
		when(skillService.create(new SkillRequest("Java", 4L))).thenReturn(page().content().get(0));

		mockMvc.perform(post("/api/master/skills").with(principal(UserRole.ADMIN)).contentType(APPLICATION_JSON)
				.content("{\"name\":\"Java\",\"categoryId\":4}"))
				.andExpect(status().isCreated());
	}

	@Test
	void memberCannotMutateSkills() throws Exception {
		mockMvc.perform(post("/api/master/skills").with(principal(UserRole.MEMBER)).contentType(APPLICATION_JSON)
				.content("{\"name\":\"Java\",\"categoryId\":4}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(put("/api/master/skills/12").with(principal(UserRole.MEMBER)).contentType(APPLICATION_JSON)
				.content("{\"name\":\"Java\",\"categoryId\":4}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/master/skills/12").with(principal(UserRole.MEMBER)))
				.andExpect(status().isForbidden());
		verifyNoSkillMutations();
	}

	@Test
	void requiresAuthenticationForSkillMutations() throws Exception {
		mockMvc.perform(post("/api/master/skills").contentType(APPLICATION_JSON)
				.content("{\"name\":\"Java\",\"categoryId\":4}"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(put("/api/master/skills/12").contentType(APPLICATION_JSON)
				.content("{\"name\":\"Java\",\"categoryId\":4}"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/master/skills/12")).andExpect(status().isUnauthorized());
		verifyNoSkillMutations();
	}

	@Test
	void validatesSkillMutationRequest() throws Exception {
		mockMvc.perform(post("/api/master/skills").with(principal(UserRole.MANAGER)).contentType(APPLICATION_JSON)
				.content("{\"name\":\"   \",\"categoryId\":0}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
		verifyNoSkillMutations();
	}

	private PageResponse<SkillResponse> page() {
		return new PageResponse<>(List.of(new SkillResponse(12L, "Java", 4L, "BACKEND", "Backend",
				Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-02T00:00:00Z"))), 0, 10, 1, 1);
	}

	private org.springframework.test.web.servlet.request.RequestPostProcessor principal(UserRole role) {
		return authentication(new UsernamePasswordAuthenticationToken(
				new UserPrincipal(7L, "user@example.com", "user", role), null,
				List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
	}

	private void verifyNoSkillMutations() {
		verify(skillService, never()).create(org.mockito.ArgumentMatchers.any(SkillRequest.class));
		verify(skillService, never()).update(org.mockito.ArgumentMatchers.anyLong(),
				org.mockito.ArgumentMatchers.any(SkillRequest.class));
		verify(skillService, never()).delete(org.mockito.ArgumentMatchers.anyLong());
	}
}
