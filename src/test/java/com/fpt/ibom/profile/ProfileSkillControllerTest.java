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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.config.SecurityConfig;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.profile.controller.ProfileSkillController;
import com.fpt.ibom.profile.dto.ProfileSkillMutationResponse;
import com.fpt.ibom.profile.dto.ProfileSkillResponse;
import com.fpt.ibom.profile.dto.ProfileVersionResponse;
import com.fpt.ibom.profile.service.ProfileSkillService;
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

@WebMvcTest(controllers = ProfileSkillController.class)
@Import({ ProfileSkillController.class, SecurityConfig.class })
@ActiveProfiles("test")
class ProfileSkillControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProfileSkillService profileSkillService;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@MockitoBean
	private UserAccountRepository userAccountRepository;

	@Test
	void mapsAllProfileSkillRoutesAndResponseWrappers() throws Exception {
		ProfileSkillResponse skill = new ProfileSkillResponse(12L, 22L, "Java", 4L, "BACKEND", "Backend",
				new BigDecimal("2.75"), LocalDate.of(2025, 1, 15));
		when(profileSkillService.list(7L, 8L)).thenReturn(List.of(skill));
		when(profileSkillService.create(eq(7L), eq(8L), any()))
				.thenReturn(new ProfileSkillMutationResponse(skill, 1L));
		when(profileSkillService.update(eq(7L), eq(8L), eq(12L), any()))
				.thenReturn(new ProfileSkillMutationResponse(skill, 2L));
		when(profileSkillService.delete(7L, 8L, 12L, 2L)).thenReturn(new ProfileVersionResponse(3L));

		mockMvc.perform(get("/api/profiles/8/skills").with(principal())).andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data[0].profileSkillId").value(12))
				.andExpect(jsonPath("$.data[0].skillId").value(22))
				.andExpect(jsonPath("$.data[0].skillName").value("Java"))
				.andExpect(jsonPath("$.data[0].categoryId").value(4))
				.andExpect(jsonPath("$.data[0].categoryCode").value("BACKEND"))
				.andExpect(jsonPath("$.data[0].categoryName").value("Backend"))
				.andExpect(jsonPath("$.data[0].experienceYears").value(2.75))
				.andExpect(jsonPath("$.data[0].lastUsed").value("2025-01-15"));
		mockMvc.perform(post("/api/profiles/8/skills").with(principal()).contentType(MediaType.APPLICATION_JSON)
				.content(requestJson(0))).andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.profileSkill.profileSkillId").value(12))
				.andExpect(jsonPath("$.data.profileVersion").value(1));
		mockMvc.perform(put("/api/profiles/8/skills/12").with(principal())
				.contentType(MediaType.APPLICATION_JSON).content(requestJson(1))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.profileVersion").value(2));
		mockMvc.perform(delete("/api/profiles/8/skills/12").with(principal())
				.contentType(MediaType.APPLICATION_JSON).content("{\"version\":2}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.profileVersion").value(3));

		verify(profileSkillService).delete(7L, 8L, 12L, 2L);
	}

	@Test
	void requiresAuthenticationAndMutationFields() throws Exception {
		mockMvc.perform(get("/api/profiles/8/skills")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/profiles/8/skills").with(principal()).contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
		mockMvc.perform(delete("/api/profiles/8/skills/12").with(principal())
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
	}

	@Test
	void mapsProfileSkillBusinessErrorsAndRejectsInvalidFields() throws Exception {
		doThrow(new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.PROFILE_SKILL_LAST_USED_IN_FUTURE, "Future date"))
				.when(profileSkillService).create(eq(7L), eq(8L), any());

		mockMvc.perform(post("/api/profiles/8/skills").with(principal()).contentType(MediaType.APPLICATION_JSON)
				.content(requestJson(0))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("PROFILE_SKILL_LAST_USED_IN_FUTURE"));

		mockMvc.perform(post("/api/profiles/8/skills").with(principal()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"skillId\":0,\"experienceYears\":-1,\"version\":-1}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
	}

	private org.springframework.test.web.servlet.request.RequestPostProcessor principal() {
		return authentication(new UsernamePasswordAuthenticationToken(
				new UserPrincipal(7L, "user@example.com", "member", UserRole.MEMBER), null, List.of()));
	}

	private String requestJson(long version) {
		return "{\"skillId\":22,\"experienceYears\":2.75,\"lastUsed\":\"2025-01-15\",\"version\":" + version + "}";
	}
}
