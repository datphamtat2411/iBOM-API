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

import java.time.LocalDate;
import java.util.List;

import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.config.SecurityConfig;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.profile.controller.ProjectController;
import com.fpt.ibom.profile.dto.ProjectMutationResponse;
import com.fpt.ibom.profile.dto.ProjectResponse;
import com.fpt.ibom.profile.dto.ProfileVersionResponse;
import com.fpt.ibom.profile.entity.ProjectStatus;
import com.fpt.ibom.profile.service.ProjectService;
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

@WebMvcTest(controllers = ProjectController.class)
@Import({ ProjectController.class, SecurityConfig.class })
@ActiveProfiles("test")
class ProjectControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProjectService projectService;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@MockitoBean
	private UserAccountRepository userAccountRepository;

	private final UserPrincipal principal = new UserPrincipal(7L, "user@example.com", "member", UserRole.MEMBER);

	@Test
	void mapsApprovedProjectRoutesAndResponseWrappers() throws Exception {
		ProjectResponse project = new ProjectResponse(12L, "Project", "Description", LocalDate.of(2020, 1, 1), null,
				ProjectStatus.ONGOING, "Engineer", 3, "Responsibilities", "Java", "Docker");
		when(projectService.list(principal, 8L)).thenReturn(List.of(project));
		when(projectService.create(eq(principal), eq(8L), any())).thenReturn(new ProjectMutationResponse(project, 1L));
		when(projectService.update(eq(principal), eq(8L), eq(12L), any())).thenReturn(new ProjectMutationResponse(project, 2L));
		when(projectService.delete(principal, 8L, 12L, 2L)).thenReturn(new ProfileVersionResponse(3L));

		mockMvc.perform(get("/api/profiles/8/projects").with(principal())).andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data[0].id").value(12))
				.andExpect(jsonPath("$.data[0].status").value("ONGOING"));
		mockMvc.perform(post("/api/profiles/8/projects").with(principal()).contentType(MediaType.APPLICATION_JSON)
				.content(requestJson(0))).andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.project.id").value(12))
				.andExpect(jsonPath("$.data.profileVersion").value(1));
		mockMvc.perform(put("/api/profiles/8/projects/12").with(principal()).contentType(MediaType.APPLICATION_JSON)
				.content(requestJson(1))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.profileVersion").value(2));
		mockMvc.perform(delete("/api/profiles/8/projects/12").with(principal())
				.contentType(MediaType.APPLICATION_JSON).content("{\"version\":2}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.profileVersion").value(3));

		verify(projectService).delete(principal, 8L, 12L, 2L);
	}

	@Test
	void requiresAuthenticationAndRequiredMutationFields() throws Exception {
		mockMvc.perform(get("/api/profiles/8/projects")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/profiles/8/projects").with(principal()).contentType(MediaType.APPLICATION_JSON)
				.content("{}" )).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
		mockMvc.perform(post("/api/profiles/8/projects").with(principal()).contentType(MediaType.APPLICATION_JSON)
				.content(requestJsonWithNullableOptionalFields(0))).andExpect(status().isCreated());
		mockMvc.perform(post("/api/profiles/8/projects").with(principal()).contentType(MediaType.APPLICATION_JSON)
				.content(requestJsonWithTeamSize(0))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
		mockMvc.perform(delete("/api/profiles/8/projects/12").with(principal())
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
	}

	@Test
	void mapsProjectBusinessErrors() throws Exception {
		doThrow(new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.PROJECT_INVALID_STATUS, "Invalid status"))
				.when(projectService).create(eq(principal), eq(8L), any());

		mockMvc.perform(post("/api/profiles/8/projects").with(principal()).contentType(MediaType.APPLICATION_JSON)
				.content(requestJson(0))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("PROJECT_INVALID_STATUS"));
	}

	private org.springframework.test.web.servlet.request.RequestPostProcessor principal() {
		return authentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
	}

	private String requestJson(long version) {
		return requestJsonWithTeamSizeAndStatus(3, "ONGOING", version);
	}

	private String requestJsonWithTeamSize(int teamSize) {
		return requestJsonWithTeamSizeAndStatus(teamSize, "ONGOING", 0);
	}

	private String requestJsonWithTeamSizeAndStatus(int teamSize, String status, long version) {
		return "{\"name\":\"Project\",\"description\":\"Description\",\"startDate\":\"2020-01-01\"," 
				+ "\"endDate\":null,\"status\":\"" + status + "\",\"position\":\"Engineer\",\"teamSize\":"
				+ teamSize + ",\"responsibilities\":\"Responsibilities\",\"programmingLanguages\":\"Java\"," 
				+ "\"tools\":\"Docker\",\"version\":" + version + "}";
	}

	private String requestJsonWithNullableOptionalFields(long version) {
		return "{\"name\":\"Project\",\"description\":\"Description\",\"status\":\"ONGOING\","
				+ "\"position\":\"Engineer\",\"version\":" + version + "}";
	}
}
