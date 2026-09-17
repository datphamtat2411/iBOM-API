package com.fpt.ibom.master;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.fpt.ibom.master.controller.SkillCategoryController;
import com.fpt.ibom.master.dto.SkillCategoryResponse;
import com.fpt.ibom.master.service.SkillCategoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SkillCategoryController.class)
@Import({ SkillCategoryController.class, SecurityConfig.class })
@ActiveProfiles("test")
class SkillCategoryControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SkillCategoryService skillCategoryService;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@MockitoBean
	private UserAccountRepository userAccountRepository;

	@ParameterizedTest
	@EnumSource(UserRole.class)
	void allowsEveryAuthenticatedRoleToReadAllControlledCategories(UserRole role) throws Exception {
		when(skillCategoryService.list()).thenReturn(categories());

		mockMvc.perform(get("/api/master/skill-categories").with(principal(role)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(200))
				.andExpect(jsonPath("$.data").isArray())
				.andExpect(jsonPath("$.data.length()").value(7))
				.andExpect(jsonPath("$.data[0].id").value(1))
				.andExpect(jsonPath("$.data[0].code").value("PROGRAMMING_LANGUAGE"))
				.andExpect(jsonPath("$.data[0].name").value("Programming Language"))
				.andExpect(jsonPath("$.data[6].id").value(7))
				.andExpect(jsonPath("$.data[6].code").value("API_MESSAGING_TESTING"))
				.andExpect(jsonPath("$.data[6].name").value("API, Messaging & Testing"));
	}

	@Test
	void requiresAuthenticationForCategoryReads() throws Exception {
		mockMvc.perform(get("/api/master/skill-categories"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(401))
				.andExpect(jsonPath("$.errorCode").value("REQUEST_FAILED"))
				.andExpect(jsonPath("$.data").doesNotExist());
	}

	@ParameterizedTest
	@EnumSource(UserRole.class)
	void exposesNoCategoryMutationRoutes(UserRole role) throws Exception {
		mockMvc.perform(post("/api/master/skill-categories").with(principal(role)))
				.andExpect(result -> assertFalse(result.getHandler() instanceof SkillCategoryController));
		mockMvc.perform(put("/api/master/skill-categories/1").with(principal(role)))
				.andExpect(result -> assertFalse(result.getHandler() instanceof SkillCategoryController));
		mockMvc.perform(delete("/api/master/skill-categories/1").with(principal(role)))
				.andExpect(result -> assertFalse(result.getHandler() instanceof SkillCategoryController));
	}

	private List<SkillCategoryResponse> categories() {
		return List.of(
				new SkillCategoryResponse(1L, "PROGRAMMING_LANGUAGE", "Programming Language"),
				new SkillCategoryResponse(2L, "FRONTEND", "Frontend"),
				new SkillCategoryResponse(3L, "BACKEND", "Backend"),
				new SkillCategoryResponse(4L, "MOBILE_GAME", "Mobile & Game"),
				new SkillCategoryResponse(5L, "DATABASE_DATA", "Database & Data"),
				new SkillCategoryResponse(6L, "CLOUD_DEVOPS", "Cloud & DevOps"),
				new SkillCategoryResponse(7L, "API_MESSAGING_TESTING", "API, Messaging & Testing"));
	}

	private org.springframework.test.web.servlet.request.RequestPostProcessor principal(UserRole role) {
		return authentication(new UsernamePasswordAuthenticationToken(
				new UserPrincipal(7L, "user@example.com", "user", role), null,
				List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role.name()))));
	}
}
