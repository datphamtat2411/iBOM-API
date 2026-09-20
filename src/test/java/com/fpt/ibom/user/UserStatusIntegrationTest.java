package com.fpt.ibom.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.fpt.ibom.MySqlIntegrationTest;
import com.fpt.ibom.auth.entity.RefreshToken;
import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.repository.RefreshTokenRepository;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.repository.ProfileRepository;
import com.fpt.ibom.user.service.UserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class UserStatusIntegrationTest extends MySqlIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private ProfileRepository profileRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private UserService userService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void persistsBothTransitionsRevokesSessionsInvalidatesAccessAndPreservesProfile() throws Exception {
		String marker = UUID.randomUUID().toString().replace("-", "");
		UserAccount manager = saveUser("manager-" + marker, UserRole.MANAGER, UserStatus.ACTIVE, "manager-password");
		UserAccount member = saveUser("member-" + marker, UserRole.MEMBER, UserStatus.ACTIVE, "member-password");
		Profile profile = profileRepository.saveAndFlush(new Profile(member, "Profile", "First", "Last", "Engineer",
				new BigDecimal("2.5"), "Personality", "Summary"));
		MvcResult login = login(member, "member-password");
		String accessToken = com.jayway.jsonpath.JsonPath.read(login.getResponse().getContentAsString(), "$.data.accessToken");
		Cookie refreshCookie = login.getResponse().getCookie("refresh_token");
		Cookie csrfCookie = login.getResponse().getCookie("XSRF-TOKEN");
		assertNotNull(refreshCookie);
		assertNotNull(csrfCookie);

		updateStatus(manager, member, "INACTIVE");
		UserAccount deactivated = userAccountRepository.findById(member.getId()).orElseThrow();
		assertEquals(UserStatus.INACTIVE, deactivated.getStatus());
		assertEquals(1L, deactivated.getAuthVersion());
		assertProfileUnchanged(profile);
		mockMvc.perform(get("/api/profiles/me").header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isUnauthorized());

		updateStatus(manager, member, "INACTIVE");
		assertEquals(1L, userAccountRepository.findById(member.getId()).orElseThrow().getAuthVersion());

		updateStatus(manager, member, "ACTIVE");
		UserAccount reactivated = userAccountRepository.findById(member.getId()).orElseThrow();
		assertEquals(UserStatus.ACTIVE, reactivated.getStatus());
		assertEquals(1L, reactivated.getAuthVersion());
		assertProfileUnchanged(profile);
		mockMvc.perform(get("/api/profiles/me").header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/auth/refresh-token").cookie(refreshCookie, csrfCookie)
				.header("X-XSRF-TOKEN", csrfCookie.getValue()))
				.andExpect(status().isUnauthorized());

		MvcResult loginAgain = login(member, "member-password");
		String newAccessToken = com.jayway.jsonpath.JsonPath.read(loginAgain.getResponse().getContentAsString(), "$.data.accessToken");
		mockMvc.perform(get("/api/profiles/me").header("Authorization", "Bearer " + newAccessToken))
				.andExpect(status().isOk());
	}

	@Test
	void migrationAddsNonNullableAuthenticationVersionWithZeroDefault() {
		assertEquals("NO", jdbcTemplate.queryForObject("SELECT IS_NULLABLE FROM information_schema.COLUMNS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'auth_version'", String.class));
		assertEquals("0", jdbcTemplate.queryForObject("SELECT COLUMN_DEFAULT FROM information_schema.COLUMNS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'auth_version'", String.class));
		assertEquals("bigint", jdbcTemplate.queryForObject("SELECT DATA_TYPE FROM information_schema.COLUMNS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'auth_version'", String.class));
	}

	@Test
	void concurrentStatusOperationsKeepFinalStatusAndRefreshRevocationConsistent() throws Exception {
		String marker = UUID.randomUUID().toString().replace("-", "");
		UserAccount manager = saveUser("manager-" + marker, UserRole.MANAGER, UserStatus.ACTIVE, "hash");
		UserAccount member = saveUser("member-" + marker, UserRole.MEMBER, UserStatus.ACTIVE, "hash");
		String tokenHash = "a".repeat(64);
		refreshTokenRepository.saveAndFlush(new RefreshToken(member, tokenHash, Instant.now().plusSeconds(3600)));
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> deactivate = executor.submit(() -> runStatus(start, member, manager, UserStatus.INACTIVE));
			Future<?> activate = executor.submit(() -> runStatus(start, member, manager, UserStatus.ACTIVE));
			start.countDown();
			deactivate.get();
			activate.get();
		} finally {
			executor.shutdownNow();
		}

		UserAccount persisted = userAccountRepository.findById(member.getId()).orElseThrow();
		long revokedSessions = jdbcTemplate.queryForObject(
				"select count(*) from refresh_tokens where token_hash = ? and revoked_at is not null", Long.class, tokenHash);
		assertEquals(1L, revokedSessions);
		assertNotNull(persisted.getStatus());
	}

	private void runStatus(CountDownLatch start, UserAccount target, UserAccount actor, UserStatus status) {
		try {
			start.await();
			userService.updateStatus(target.getId(), actor.getId(), status);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(exception);
		}
	}

	private MvcResult login(UserAccount user, String password) throws Exception {
		return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + password + "\"}"))
				.andExpect(status().isOk()).andReturn();
	}

	private void updateStatus(UserAccount manager, UserAccount target, String status) throws Exception {
		mockMvc.perform(put("/api/users/{userId}/status", target.getId()).with(authentication(userPrincipal(manager)))
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value(status));
	}

	private UserAccount saveUser(String username, UserRole role, UserStatus status, String password) {
		return userAccountRepository.saveAndFlush(new UserAccount(username + "@example.com", username,
				passwordEncoder.encode(password), role, status));
	}

	private void assertProfileUnchanged(Profile original) {
		Profile persisted = profileRepository.findById(original.getId()).orElseThrow();
		assertEquals(original.getProfileName(), persisted.getProfileName());
		assertEquals(original.getFirstName(), persisted.getFirstName());
		assertEquals(original.getLastName(), persisted.getLastName());
		assertEquals(original.getJobTitle(), persisted.getJobTitle());
		assertEquals(0, original.getYearsOfExperience().compareTo(persisted.getYearsOfExperience()));
		assertEquals(original.getPersonality(), persisted.getPersonality());
		assertEquals(original.getTechnicalSummary(), persisted.getTechnicalSummary());
		assertEquals(original.getVersion(), persisted.getVersion());
	}

	private Authentication userPrincipal(UserAccount user) {
		return new UsernamePasswordAuthenticationToken(
				new UserPrincipal(user.getId(), user.getEmail(), user.getUsername(), user.getRole()), null,
				List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
	}
}
