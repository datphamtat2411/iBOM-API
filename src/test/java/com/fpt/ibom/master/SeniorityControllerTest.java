package com.fpt.ibom.master;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.core.authority.AuthorityUtils.createAuthorityList;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.config.SecurityConfig;
import com.fpt.ibom.master.controller.SeniorityController;
import com.fpt.ibom.master.dto.SeniorityResponse;
import com.fpt.ibom.master.service.SeniorityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SeniorityController.class)
@Import({ SeniorityController.class, SecurityConfig.class })
@ActiveProfiles("test")
class SeniorityControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SeniorityService seniorityService;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@MockitoBean
	private UserAccountRepository userAccountRepository;

	@Test
	void mapsAllCrudRoutesAndCommonResponseStructure() throws Exception {
		SeniorityResponse response = new SeniorityResponse(12L, "Junior", new BigDecimal("0.00"), new BigDecimal("2.00"));
		when(seniorityService.list()).thenReturn(List.of(response));
		when(seniorityService.create(any())).thenReturn(response);
		when(seniorityService.update(any(Long.class), any())).thenReturn(response);

		mockMvc.perform(get("/api/master/seniority").with(principal(UserRole.MEMBER)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
				.andExpect(jsonPath("$.data[0].id").value(12)).andExpect(jsonPath("$.data[0].name").value("Junior"))
				.andExpect(jsonPath("$.data[0].fromExperience").value(0.0))
				.andExpect(jsonPath("$.data[0].toExperience").value(2.0));
		mockMvc.perform(post("/api/master/seniority").with(principal(UserRole.MANAGER)).contentType(MediaType.APPLICATION_JSON)
				.content(requestJson("Junior", "0", "2"))).andExpect(status().isCreated())
				.andExpect(jsonPath("$.code").value(201));
		mockMvc.perform(put("/api/master/seniority/12").with(principal(UserRole.ADMIN)).contentType(MediaType.APPLICATION_JSON)
				.content(requestJson("Junior", "0", "2"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(12));
		mockMvc.perform(delete("/api/master/seniority/12").with(principal(UserRole.MANAGER)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
	}

	@Test
	void allowsAllRolesToReadAndOnlyManagerAndAdminToMutate() throws Exception {
		when(seniorityService.list()).thenReturn(List.of());
		for (UserRole role : UserRole.values()) {
			mockMvc.perform(get("/api/master/seniority").with(principal(role))).andExpect(status().isOk());
		}
		for (UserRole role : List.of(UserRole.MEMBER)) {
			mockMvc.perform(post("/api/master/seniority").with(principal(role)).contentType(MediaType.APPLICATION_JSON)
					.content(requestJson("Junior", "0", "2"))).andExpect(status().isForbidden());
			mockMvc.perform(put("/api/master/seniority/1").with(principal(role)).contentType(MediaType.APPLICATION_JSON)
					.content(requestJson("Junior", "0", "2"))).andExpect(status().isForbidden());
			mockMvc.perform(delete("/api/master/seniority/1").with(principal(role))).andExpect(status().isForbidden());
		}
	}

	@Test
	void validatesMutationFieldsAndRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/master/seniority")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/master/seniority").with(principal(UserRole.MANAGER)).contentType(MediaType.APPLICATION_JSON)
				.content("{}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
	}

	private org.springframework.test.web.servlet.request.RequestPostProcessor principal(UserRole role) {
		return authentication(new UsernamePasswordAuthenticationToken(
				new UserPrincipal(7L, "user@example.com", "user", role), null,
				createAuthorityList("ROLE_" + role.name())));
	}

	private String requestJson(String name, String from, String to) {
		return "{\"name\":\"" + name + "\",\"fromExperience\":" + from + ",\"toExperience\":" + to + "}";
	}
}
