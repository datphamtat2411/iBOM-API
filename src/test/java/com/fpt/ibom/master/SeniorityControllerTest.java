package com.fpt.ibom.master;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.core.authority.AuthorityUtils.createAuthorityList;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.config.SecurityConfig;
import com.fpt.ibom.master.controller.SeniorityController;
import com.fpt.ibom.master.dto.SeniorityMutationRequest;
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
		SeniorityResponse response = new SeniorityResponse(12L, "Junior", new BigDecimal("0.00"), new BigDecimal("2.00"),
				Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-02T00:00:00Z"));
		when(seniorityService.list()).thenReturn(List.of(response));
		when(seniorityService.create(any())).thenReturn(response);
		when(seniorityService.update(any(Long.class), any())).thenReturn(response);

		mockMvc.perform(get("/api/master/seniority").with(principal(UserRole.MEMBER)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
				.andExpect(jsonPath("$.data[0].id").value(12)).andExpect(jsonPath("$.data[0].name").value("Junior"))
				.andExpect(jsonPath("$.data[0].fromExperience").value(0.0))
				.andExpect(jsonPath("$.data[0].toExperience").value(2.0))
				.andExpect(jsonPath("$.data[0].createdAt").value("2026-01-01T00:00:00Z"))
				.andExpect(jsonPath("$.data[0].updatedAt").value("2026-01-02T00:00:00Z"));
		mockMvc.perform(post("/api/master/seniority").with(principal(UserRole.MANAGER)).contentType(MediaType.APPLICATION_JSON)
				.content(requestJson("Junior", "0", "2"))).andExpect(status().isCreated())
				.andExpect(jsonPath("$.code").value(201))
				.andExpect(jsonPath("$.data.createdAt").value("2026-01-01T00:00:00Z"))
				.andExpect(jsonPath("$.data.updatedAt").value("2026-01-02T00:00:00Z"));
		mockMvc.perform(put("/api/master/seniority/12").with(principal(UserRole.ADMIN)).contentType(MediaType.APPLICATION_JSON)
				.content(requestJson("Junior", "0", "2"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(12))
				.andExpect(jsonPath("$.data.createdAt").value("2026-01-01T00:00:00Z"))
				.andExpect(jsonPath("$.data.updatedAt").value("2026-01-02T00:00:00Z"));
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

	@Test
	void rejectsDecimalValuesOutsideDatabasePrecisionBeforeService() throws Exception {
		mockMvc.perform(post("/api/master/seniority").with(principal(UserRole.MANAGER))
				.contentType(MediaType.APPLICATION_JSON).content(requestJson("Large from", "1000.00", "1001.00")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
		mockMvc.perform(post("/api/master/seniority").with(principal(UserRole.MANAGER))
				.contentType(MediaType.APPLICATION_JSON).content(requestJson("Precise from", "0.001", "1.00")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
		mockMvc.perform(put("/api/master/seniority/12").with(principal(UserRole.MANAGER))
				.contentType(MediaType.APPLICATION_JSON).content(requestJson("Large to", "0.00", "1000.00")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
		mockMvc.perform(put("/api/master/seniority/12").with(principal(UserRole.MANAGER))
				.contentType(MediaType.APPLICATION_JSON).content(requestJson("Precise to", "0.00", "1.001")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

		verifyNoInteractions(seniorityService);
	}

	@Test
	void acceptsDecimalBoundariesAndNullableUnlimitedOnMutations() throws Exception {
		SeniorityMutationRequest unlimited = new SeniorityMutationRequest("Maximum", new BigDecimal("999.99"), null);
		SeniorityMutationRequest finite = new SeniorityMutationRequest("Minimum", new BigDecimal("0.00"),
				new BigDecimal("999.99"));

		mockMvc.perform(post("/api/master/seniority").with(principal(UserRole.MANAGER))
				.contentType(MediaType.APPLICATION_JSON).content(requestJson("Maximum", "999.99", null)))
				.andExpect(status().isCreated());
		mockMvc.perform(put("/api/master/seniority/12").with(principal(UserRole.ADMIN))
				.contentType(MediaType.APPLICATION_JSON).content(requestJson("Minimum", "0.00", "999.99")))
				.andExpect(status().isOk());

		verify(seniorityService).create(unlimited);
		verify(seniorityService).update(12L, finite);
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
