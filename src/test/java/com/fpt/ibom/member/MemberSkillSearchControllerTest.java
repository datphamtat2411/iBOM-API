package com.fpt.ibom.member;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.common.PageResponse;
import com.fpt.ibom.config.SecurityConfig;
import com.fpt.ibom.member.controller.MemberController;
import com.fpt.ibom.member.dto.MatchingProfileResponse;
import com.fpt.ibom.member.dto.MemberSkillSearchRequest;
import com.fpt.ibom.member.dto.MemberSkillSearchResponse;
import com.fpt.ibom.member.dto.SkillSeniorityMatchResponse;
import com.fpt.ibom.member.service.MemberService;
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

@WebMvcTest(controllers = MemberController.class)
@Import({ MemberController.class, SecurityConfig.class })
@ActiveProfiles("test")
class MemberSkillSearchControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private MemberService memberService;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@MockitoBean
	private UserAccountRepository userAccountRepository;

	@Test
	void bindsRepeatedListsByIndexAndAppliesDefaults() throws Exception {
		MemberSkillSearchRequest request = new MemberSkillSearchRequest(List.of(1L, 3L), List.of(2L, 4L), null, 0, 10);
		when(memberService.searchBySkill(request)).thenReturn(page());

		mockMvc.perform(get("/api/members/search-by-skill").param("skillIds", "1", "3")
				.param("seniorityIds", "2", "4").with(principal(UserRole.MANAGER)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
				.andExpect(jsonPath("$.data.content[0].matchingProfiles[0].matches[0].skillId").value(1))
				.andExpect(jsonPath("$.data.content[0].matchingProfiles[0].matches[0].seniorityId").value(2));
		verify(memberService).searchBySkill(eq(request));

		MemberSkillSearchRequest explicit = new MemberSkillSearchRequest(List.of(1L), List.of(2L), UserStatus.INACTIVE, 2, 1);
		when(memberService.searchBySkill(explicit)).thenReturn(page());
		mockMvc.perform(get("/api/members/search-by-skill").param("skillIds", "1").param("seniorityIds", "2")
				.param("status", "INACTIVE").param("page", "2").param("size", "1")
				.with(principal(UserRole.ADMIN))).andExpect(status().isOk());
		verify(memberService).searchBySkill(eq(explicit));
	}

	@Test
	void rejectsMissingMalformedAndInvalidQueryParametersAsValidationErrors() throws Exception {
		mockMvc.perform(get("/api/members/search-by-skill").param("seniorityIds", "2")
				.with(principal(UserRole.MANAGER))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
		mockMvc.perform(get("/api/members/search-by-skill").param("skillIds", "not-a-number")
				.param("seniorityIds", "2").with(principal(UserRole.MANAGER))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
		mockMvc.perform(get("/api/members/search-by-skill").param("skillIds", "1").param("seniorityIds", "2")
				.param("status", "UNKNOWN").with(principal(UserRole.MANAGER))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
	}

	@Test
	void requiresManagerOrAdmin() throws Exception {
		mockMvc.perform(get("/api/members/search-by-skill").param("skillIds", "1").param("seniorityIds", "2"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/members/search-by-skill").param("skillIds", "1").param("seniorityIds", "2")
				.with(principal(UserRole.MEMBER))).andExpect(status().isForbidden());
	}

	@ParameterizedTest
	@EnumSource(value = UserRole.class, names = { "MANAGER", "ADMIN" })
	void managerAndAdminCanAccess(UserRole role) throws Exception {
		when(memberService.searchBySkill(new MemberSkillSearchRequest(List.of(1L), List.of(2L), null, 0, 10)))
				.thenReturn(page());
		mockMvc.perform(get("/api/members/search-by-skill").param("skillIds", "1").param("seniorityIds", "2")
				.with(principal(role))).andExpect(status().isOk());
	}

	private PageResponse<MemberSkillSearchResponse> page() {
		return new PageResponse<>(List.of(new MemberSkillSearchResponse(12L, "Alice", "alice@example.com",
				UserStatus.ACTIVE, 1L, Instant.parse("2026-01-04T00:00:00Z"), List.of(
						new MatchingProfileResponse(22L, "CV", "A", "One", "Engineer",
								Instant.parse("2026-01-03T00:00:00Z"), List.of(
										new SkillSeniorityMatchResponse(1L, 2L, new BigDecimal("2.00"))))))), 0, 10, 1, 1);
	}

	private org.springframework.test.web.servlet.request.RequestPostProcessor principal(UserRole role) {
		return authentication(new UsernamePasswordAuthenticationToken(
				new UserPrincipal(7L, "user@example.com", "user", role), null,
				List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
	}
}
