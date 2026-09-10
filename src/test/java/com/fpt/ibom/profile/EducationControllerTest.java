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
import com.fpt.ibom.profile.controller.EducationController;
import com.fpt.ibom.profile.dto.EducationMutationResponse;
import com.fpt.ibom.profile.dto.EducationResponse;
import com.fpt.ibom.profile.dto.ProfileVersionResponse;
import com.fpt.ibom.profile.entity.EducationStatus;
import com.fpt.ibom.profile.service.EducationService;
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

@WebMvcTest(controllers = EducationController.class)
@Import({ EducationController.class, SecurityConfig.class })
@ActiveProfiles("test")
class EducationControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private EducationService educationService;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@MockitoBean
	private UserAccountRepository userAccountRepository;

	@Test
	void mapsEducationCrudRoutesAndResponseWrappers() throws Exception {
		EducationResponse education = new EducationResponse(12L, "School", "Degree", "Field",
				LocalDate.of(2020, 1, 1), null, EducationStatus.ONGOING);
		when(educationService.list(7L, 8L)).thenReturn(List.of(education));
		when(educationService.create(eq(7L), eq(8L), any())).thenReturn(new EducationMutationResponse(education, 1L));
		when(educationService.update(eq(7L), eq(8L), eq(12L), any()))
				.thenReturn(new EducationMutationResponse(education, 2L));
		when(educationService.delete(7L, 8L, 12L, 2L)).thenReturn(new ProfileVersionResponse(3L));

		mockMvc.perform(get("/api/profiles/8/educations").with(principal())).andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data[0].id").value(12))
				.andExpect(jsonPath("$.data[0].status").value("ONGOING"));
		mockMvc.perform(post("/api/profiles/8/educations").with(principal()).contentType(MediaType.APPLICATION_JSON)
				.content(requestJson(0))).andExpect(status().isCreated()).andExpect(jsonPath("$.data.education.id").value(12))
				.andExpect(jsonPath("$.data.profileVersion").value(1));
		mockMvc.perform(put("/api/profiles/8/educations/12").with(principal()).contentType(MediaType.APPLICATION_JSON)
				.content(requestJson(1))).andExpect(status().isOk()).andExpect(jsonPath("$.data.profileVersion").value(2));
		mockMvc.perform(delete("/api/profiles/8/educations/12").with(principal())
				.contentType(MediaType.APPLICATION_JSON).content("{\"version\":2}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.profileVersion").value(3));

		verify(educationService).delete(7L, 8L, 12L, 2L);
	}

	@Test
	void requiresAuthenticationAndMutationFields() throws Exception {
		mockMvc.perform(get("/api/profiles/8/educations")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/profiles/8/educations").with(principal()).contentType(MediaType.APPLICATION_JSON)
				.content("{}" )).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
		mockMvc.perform(delete("/api/profiles/8/educations/12").with(principal()).contentType(MediaType.APPLICATION_JSON)
				.content("{}" )).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
	}

	@Test
	void mapsEducationBusinessErrors() throws Exception {
		doThrow(new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.EDUCATION_INVALID_STATUS, "Invalid status"))
				.when(educationService).create(eq(7L), eq(8L), any());

		mockMvc.perform(post("/api/profiles/8/educations").with(principal()).contentType(MediaType.APPLICATION_JSON)
				.content(requestJson(0))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("EDUCATION_INVALID_STATUS"));
	}

	@Test
	void rejectsUnsupportedStatusAsEducationError() throws Exception {
		doThrow(new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.EDUCATION_INVALID_STATUS, "Invalid status"))
				.when(educationService).create(eq(7L), eq(8L), any());

		mockMvc.perform(post("/api/profiles/8/educations").with(principal()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"schoolName\":\"School\",\"degree\":\"Degree\",\"startDate\":\"2020-01-01\","
						+ "\"status\":\"INVALID\",\"version\":0}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("EDUCATION_INVALID_STATUS"));
	}

	private org.springframework.test.web.servlet.request.RequestPostProcessor principal() {
		return authentication(new UsernamePasswordAuthenticationToken(
				new UserPrincipal(7L, "user@example.com", "member", UserRole.MEMBER), null, List.of()));
	}

	private String requestJson(long version) {
		return "{\"schoolName\":\"School\",\"degree\":\"Degree\",\"fieldOfStudy\":\"Field\","
				+ "\"startDate\":\"2020-01-01\",\"status\":\"ONGOING\",\"version\":" + version + "}";
	}
}
