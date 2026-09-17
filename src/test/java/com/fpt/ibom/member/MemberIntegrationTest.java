package com.fpt.ibom.member;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fpt.ibom.MySqlIntegrationTest;
import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.repository.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MemberIntegrationTest extends MySqlIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private ProfileRepository profileRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void listsOnlyMembersWithOrderingCountsTimestampAndFilters() throws Exception {
		UserAccount alpha = saveUser("alpha-" + UUID.randomUUID(), UserRole.MEMBER, UserStatus.ACTIVE);
		UserAccount beta = saveUser("beta-" + UUID.randomUUID(), UserRole.MEMBER, UserStatus.ACTIVE);
		UserAccount inactive = saveUser("zulu-" + UUID.randomUUID(), UserRole.MEMBER, UserStatus.INACTIVE);
		UserAccount manager = saveUser("manager-" + UUID.randomUUID(), UserRole.MANAGER, UserStatus.ACTIVE);
		Profile activeProfile = profileRepository.saveAndFlush(profile(alpha, "active"));
		Profile deletedProfile = profileRepository.saveAndFlush(profile(alpha, "deleted"));
		deletedProfile.softDelete(Instant.parse("2026-01-05T00:00:00Z"));
		profileRepository.saveAndFlush(deletedProfile);
		setUpdatedAt(alpha, Instant.parse("2026-01-03T00:00:00Z"));
		setUpdatedAt(activeProfile, Instant.parse("2026-01-02T00:00:00Z"));
		setUpdatedAt(deletedProfile, Instant.parse("2026-01-05T00:00:00Z"));

		mockMvc.perform(get("/api/members").param("size", "10").with(authentication(userPrincipal(manager))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(3))
				.andExpect(jsonPath("$.data.content[0].username").value(alpha.getUsername()))
				.andExpect(jsonPath("$.data.content[1].username").value(beta.getUsername()))
				.andExpect(jsonPath("$.data.content[2].username").value(inactive.getUsername()))
				.andExpect(jsonPath("$.data.content[0].activeProfileCount").value(1))
				.andExpect(jsonPath("$.data.content[0].lastUpdatedAt").value("2026-01-05T07:00:00Z"));

		mockMvc.perform(get("/api/members").param("status", "ACTIVE").param("search", "ALPHA")
				.with(authentication(userPrincipal(manager))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
		mockMvc.perform(get("/api/members").param("search", "@EXAMPLE.COM")
				.with(authentication(userPrincipal(manager))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(3));
	}

	@Test
	void paginatesRecoversToLastPageAndNormalizesEmptyResults() throws Exception {
		UserAccount first = saveUser("page-first-" + UUID.randomUUID(), UserRole.MEMBER, UserStatus.ACTIVE);
		UserAccount second = saveUser("page-second-" + UUID.randomUUID(), UserRole.MEMBER, UserStatus.ACTIVE);
		UserAccount manager = saveUser("page-manager-" + UUID.randomUUID(), UserRole.MANAGER, UserStatus.ACTIVE);

		mockMvc.perform(get("/api/members").param("size", "1").param("page", "9").param("search", "page-")
				.with(authentication(userPrincipal(manager))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.page").value(1))
				.andExpect(jsonPath("$.data.size").value(1)).andExpect(jsonPath("$.data.totalElements").value(2))
				.andExpect(jsonPath("$.data.totalPages").value(2))
				.andExpect(jsonPath("$.data.content[0].username").value(second.getUsername()));

		mockMvc.perform(get("/api/members").param("search", "no-such-member")
				.with(authentication(userPrincipal(manager))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.page").value(0))
				.andExpect(jsonPath("$.data.totalElements").value(0)).andExpect(jsonPath("$.data.totalPages").value(0))
				.andExpect(jsonPath("$.data.content").isEmpty());
		assertEquals(UserRole.MEMBER, userAccountRepository.findById(first.getId()).orElseThrow().getRole());
	}

	private UserAccount saveUser(String username, UserRole role, UserStatus status) {
		return userAccountRepository.saveAndFlush(new UserAccount(username + "@example.com", username, "hash", role, status));
	}

	private Profile profile(UserAccount user, String name) {
		return new Profile(user, name + "-" + UUID.randomUUID(), "First", "Last", "Engineer", BigDecimal.ONE,
				"personality", "summary");
	}

	private void setUpdatedAt(UserAccount user, Instant updatedAt) {
		jdbcTemplate.update("UPDATE users SET updated_at = ? WHERE id = ?", Timestamp.from(updatedAt), user.getId());
	}

	private void setUpdatedAt(Profile profile, Instant updatedAt) {
		jdbcTemplate.update("UPDATE profiles SET updated_at = ? WHERE id = ?", Timestamp.from(updatedAt), profile.getId());
	}

	private Authentication userPrincipal(UserAccount user) {
		return new UsernamePasswordAuthenticationToken(
				new UserPrincipal(user.getId(), user.getEmail(), user.getUsername(), user.getRole()), null,
				List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
	}
}
