package com.fpt.ibom.member;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.fpt.ibom.member.dto.MemberSearchRequest;
import com.fpt.ibom.member.dto.MemberSearchResponse;
import com.fpt.ibom.member.dto.MemberSummaryResponse;
import com.fpt.ibom.member.service.MemberService;
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

@WebMvcTest(controllers = MemberController.class)
@Import({ MemberController.class, SecurityConfig.class })
@ActiveProfiles("test")
class MemberControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private MemberService memberService;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@MockitoBean
	private UserAccountRepository userAccountRepository;

	@Test
	void appliesDefaultAndExplicitQueryParametersAndExposesOnlyMemberFields() throws Exception {
		when(memberService.list(0, 10, null, null)).thenReturn(page());
		mockMvc.perform(get("/api/members").with(principal(UserRole.MANAGER)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
				.andExpect(jsonPath("$.data.content[0].id").value(12))
				.andExpect(jsonPath("$.data.content[0].username").value("Alice"))
				.andExpect(jsonPath("$.data.content[0].email").value("alice@example.com"))
				.andExpect(jsonPath("$.data.content[0].status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.content[0].activeProfileCount").value(2))
				.andExpect(jsonPath("$.data.content[0].lastUpdatedAt").value("2026-01-04T00:00:00Z"))
				.andExpect(jsonPath("$.data.content[0].fullName").doesNotExist())
				.andExpect(jsonPath("$.data.content[0].jobTitle").doesNotExist());
		verify(memberService).list(0, 10, null, null);

		when(memberService.list(2, 1, "  alice  ", UserStatus.INACTIVE)).thenReturn(page());
		mockMvc.perform(get("/api/members").param("page", "2").param("size", "1")
				.param("search", "  alice  ").param("status", "INACTIVE").with(principal(UserRole.ADMIN)))
				.andExpect(status().isOk());
		verify(memberService).list(2, 1, "  alice  ", UserStatus.INACTIVE);
	}

	@Test
	void requiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/members")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(401));
	}

	@Test
	void memberIsForbidden() throws Exception {
		mockMvc.perform(get("/api/members").with(principal(UserRole.MEMBER)))
				.andExpect(status().isForbidden());
	}

	@ParameterizedTest
	@EnumSource(value = UserRole.class, names = { "MANAGER", "ADMIN" })
	void managerAndAdminCanAccess(UserRole role) throws Exception {
		when(memberService.list(0, 10, null, null)).thenReturn(page());

		mockMvc.perform(get("/api/members").with(principal(role))).andExpect(status().isOk());
	}

	@Test
	void rejectsInvalidStatusInput() throws Exception {
		mockMvc.perform(get("/api/members").param("status", "UNKNOWN").with(principal(UserRole.MANAGER)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void searchesWithPostAndReturnsMatchingProfiles() throws Exception {
		MemberSearchRequest request = new MemberSearchRequest("alice", "INACTIVE",
				List.of(new MemberSearchRequest.SkillCondition(1L, null)),
				List.of(new MemberSearchRequest.LanguageCondition(2L, "NATIVE")), 2, 1);
		when(memberService.search(request)).thenReturn(searchPage());

		mockMvc.perform(post("/api/members/search").with(principal(UserRole.MANAGER))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"search":"alice","status":"INACTIVE","skills":[{"skillId":1}],
						"languages":[{"languageId":2,"level":"NATIVE"}],"page":2,"size":1}
						"""))
				.andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
				.andExpect(jsonPath("$.data.content[0].matchingProfiles[0].id").value(18))
				.andExpect(jsonPath("$.data.content[0].matchingProfiles[0].profileName").value("Primary"));
		verify(memberService).search(request);
	}

	@Test
	void postSearchRequiresManagerOrAdmin() throws Exception {
		String request = "{\"skills\":[{\"skillId\":1}],\"page\":0,\"size\":10}";
		mockMvc.perform(post("/api/members/search").contentType(MediaType.APPLICATION_JSON).content(request))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/members/search").with(principal(UserRole.MEMBER))
				.contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isForbidden());
	}

	private PageResponse<MemberSummaryResponse> page() {
		return new PageResponse<>(List.of(new MemberSummaryResponse(12L, "Alice", "alice@example.com",
				UserStatus.ACTIVE, 2L, Instant.parse("2026-01-04T00:00:00Z"))), 0, 10, 1, 1);
	}

	private PageResponse<MemberSearchResponse> searchPage() {
		return new PageResponse<>(List.of(new MemberSearchResponse(12L, "Alice", "alice@example.com",
				UserStatus.INACTIVE, 2L, Instant.parse("2026-01-04T00:00:00Z"),
				List.of(new MatchingProfileResponse(18L, "Primary", "First", "Last", "Engineer",
						Instant.parse("2026-01-04T00:00:00Z"))))), 2, 1, 3, 3);
	}

	private org.springframework.test.web.servlet.request.RequestPostProcessor principal(UserRole role) {
		return authentication(new UsernamePasswordAuthenticationToken(
				new UserPrincipal(7L, "user@example.com", "user", role), null,
				List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
	}
}
