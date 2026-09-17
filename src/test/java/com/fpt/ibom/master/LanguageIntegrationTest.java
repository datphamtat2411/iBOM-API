package com.fpt.ibom.master;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.fpt.ibom.MySqlIntegrationTest;
import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.master.entity.Language;
import com.fpt.ibom.master.repository.LanguageRepository;
import com.fpt.ibom.profile.entity.LanguageLevel;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.entity.ProfileLanguage;
import com.fpt.ibom.profile.repository.ProfileLanguageRepository;
import com.fpt.ibom.profile.repository.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class LanguageIntegrationTest extends MySqlIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private LanguageRepository languageRepository;

	@Autowired
	private UserAccountRepository userRepository;

	@Autowired
	private ProfileRepository profileRepository;

	@Autowired
	private ProfileLanguageRepository profileLanguageRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void migrationCreatesLanguageSchemaAndApprovedSeedRows() {
		assertTrue(jdbcTemplate.queryForList("SELECT TABLE_NAME FROM information_schema.TABLES "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'languages'", String.class).contains("languages"));
		assertEquals("NO", jdbcTemplate.queryForObject("SELECT IS_NULLABLE FROM information_schema.COLUMNS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'languages' AND COLUMN_NAME = 'name'", String.class));
		assertEquals("NO", jdbcTemplate.queryForObject("SELECT IS_NULLABLE FROM information_schema.COLUMNS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'languages' AND COLUMN_NAME = 'created_at'", String.class));
		assertEquals("NO", jdbcTemplate.queryForObject("SELECT IS_NULLABLE FROM information_schema.COLUMNS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'languages' AND COLUMN_NAME = 'updated_at'", String.class));
		assertEquals(6, jdbcTemplate.queryForObject("SELECT DATETIME_PRECISION FROM information_schema.COLUMNS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'languages' AND COLUMN_NAME = 'created_at'", Integer.class));
		assertEquals(6, jdbcTemplate.queryForObject("SELECT DATETIME_PRECISION FROM information_schema.COLUMNS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'languages' AND COLUMN_NAME = 'updated_at'", Integer.class));
		assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'profile_languages'", Integer.class));

		for (String name : List.of("English", "Vietnamese", "Japanese")) {
			MapRow row = jdbcTemplate.queryForObject("SELECT id, created_at, updated_at FROM languages WHERE name = ?",
					(rs, rowNum) -> new MapRow(rs.getLong("id"), rs.getTimestamp("created_at"), rs.getTimestamp("updated_at")), name);
			assertNotNull(row);
			assertTrue(row.id() > 0);
			assertNotNull(row.createdAt());
			assertNotNull(row.updatedAt());
		}
	}

	@Test
	void databaseEnforcesTrimmedNamesAndCaseInsensitiveUniqueness() {
		assertDatabaseIntegrityViolation(() -> languageRepository.saveAndFlush(new Language("   ")));
		assertDatabaseIntegrityViolation(() -> languageRepository.saveAndFlush(new Language("english")));
	}

	@Test
	void languageReadRouteSearchesOrdersPaginatesAndReturnsTimestamps() throws Exception {
		String marker = UUID.randomUUID().toString().substring(0, 8);
		String prefix = "language-" + marker;
		languageRepository.saveAndFlush(new Language(prefix + "-Zulu"));
		languageRepository.saveAndFlush(new Language(prefix + "-Alpha"));
		languageRepository.saveAndFlush(new Language(prefix + "-Mike"));
		UserAccount user = saveUser();

		mockMvc.perform(get("/api/master/languages").param("page", "0").param("size", "2")
				.param("search", prefix.toUpperCase(Locale.ROOT)).with(authentication(userPrincipal(user))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.page").value(0))
				.andExpect(jsonPath("$.data.size").value(2))
				.andExpect(jsonPath("$.data.totalElements").value(3))
				.andExpect(jsonPath("$.data.totalPages").value(2))
				.andExpect(jsonPath("$.data.content[0].name").value(prefix + "-Alpha"))
				.andExpect(jsonPath("$.data.content[1].name").value(prefix + "-Mike"))
				.andExpect(jsonPath("$.data.content[0].createdAt").isNotEmpty())
				.andExpect(jsonPath("$.data.content[0].updatedAt").isNotEmpty());

		mockMvc.perform(get("/api/master/languages").param("page", "1").param("size", "2")
				.param("search", "  " + prefix + "  ").with(authentication(userPrincipal(user))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[0].name").value(prefix + "-Zulu"));
	}

	@Test
	void languageCrudTrimsNamesAndRestrictsMemberMutations() throws Exception {
		UserAccount manager = saveUser(UserRole.MANAGER);
		UserAccount member = saveUser(UserRole.MEMBER);

		String name = "Managed-" + UUID.randomUUID();
		var created = mockMvc.perform(post("/api/master/languages").with(authentication(userPrincipal(manager)))
				.contentType(APPLICATION_JSON).content("{\"name\":\"  " + name + "  \"}"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.data.name").value(name))
				.andReturn();
		long languageId = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
				.readTree(created.getResponse().getContentAsString()).get("data").get("id").asLong();

		mockMvc.perform(put("/api/master/languages/" + languageId).with(authentication(userPrincipal(manager)))
				.contentType(APPLICATION_JSON).content("{\"name\":\"  Updated-" + name + "  \"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Updated-" + name));
		mockMvc.perform(delete("/api/master/languages/" + languageId).with(authentication(userPrincipal(member))))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/master/languages/" + languageId).with(authentication(userPrincipal(manager))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());
		assertTrue(languageRepository.findById(languageId).isEmpty());
	}

	@Test
	void languageCrudRejectsDuplicateAndUnknownIds() throws Exception {
		UserAccount manager = saveUser(UserRole.MANAGER);
		String name = "Duplicate-" + UUID.randomUUID();
		mockMvc.perform(post("/api/master/languages").with(authentication(userPrincipal(manager))).contentType(APPLICATION_JSON)
				.content("{\"name\":\"" + name + "\"}"))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/master/languages").with(authentication(userPrincipal(manager))).contentType(APPLICATION_JSON)
				.content("{\"name\":\"  " + name.toUpperCase(Locale.ROOT) + "  \"}"))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("LANGUAGE_ALREADY_EXISTS"));
		mockMvc.perform(put("/api/master/languages/999999999").with(authentication(userPrincipal(manager)))
				.contentType(APPLICATION_JSON).content("{\"name\":\"English\"}"))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("LANGUAGE_NOT_FOUND"));
	}

	@Test
	void referencedLanguageCannotBeDeletedAndProfileLanguageRemains() throws Exception {
		UserAccount manager = saveUser(UserRole.MANAGER);
		UserAccount owner = saveUser(UserRole.MEMBER);
		Profile profile = profileRepository.saveAndFlush(new Profile(owner, "profile-" + UUID.randomUUID(), "First",
				"Last", "Developer", java.math.BigDecimal.ZERO, null, null));
		Language language = languageRepository.saveAndFlush(new Language("Referenced-" + UUID.randomUUID()));
		ProfileLanguage profileLanguage = profileLanguageRepository.saveAndFlush(
				new ProfileLanguage(profile, language, LanguageLevel.BEGINNER));

		mockMvc.perform(delete("/api/master/languages/" + language.getId()).with(authentication(userPrincipal(manager))))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("LANGUAGE_IN_USE"));
		assertTrue(profileLanguageRepository.findById(profileLanguage.getId()).isPresent());
		assertTrue(languageRepository.findById(language.getId()).isPresent());
	}

	private UserAccount saveUser() {
		return saveUser(UserRole.MEMBER);
	}

	private UserAccount saveUser(UserRole role) {
		return userRepository.saveAndFlush(new UserAccount(UUID.randomUUID() + "@example.com",
				"language-user-" + UUID.randomUUID(), "hash", role, UserStatus.ACTIVE));
	}

	private Authentication userPrincipal(UserAccount user) {
		return new UsernamePasswordAuthenticationToken(
				new UserPrincipal(user.getId(), user.getEmail(), user.getUsername(), user.getRole()), null,
				List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
	}

	private void assertDatabaseIntegrityViolation(Runnable action) {
		RuntimeException exception = assertThrows(RuntimeException.class, action::run);
		assertTrue(exception instanceof DataIntegrityViolationException || exception instanceof JpaSystemException);
	}

	private record MapRow(long id, java.sql.Timestamp createdAt, java.sql.Timestamp updatedAt) {
	}
}
