package com.fpt.ibom.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.fpt.ibom.MySqlIntegrationTest;
import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.master.entity.Language;
import com.fpt.ibom.master.repository.LanguageRepository;
import com.fpt.ibom.profile.dto.ProfileLanguageRequest;
import com.fpt.ibom.profile.entity.LanguageLevel;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.entity.ProfileLanguage;
import com.fpt.ibom.profile.repository.ProfileLanguageRepository;
import com.fpt.ibom.profile.repository.ProfileRepository;
import com.fpt.ibom.profile.service.ProfileLanguageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ProfileLanguageIntegrationTest extends MySqlIntegrationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ProfileLanguageService profileLanguageService;
	@Autowired
	private ProfileLanguageRepository profileLanguageRepository;
	@Autowired
	private ProfileRepository profileRepository;
	@Autowired
	private LanguageRepository languageRepository;
	@Autowired
	private UserAccountRepository userRepository;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void migrationCreatesProfileLanguageSchemaAndDatabaseConstraints() {
		assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'profile_languages'", Integer.class));
		assertEquals("varchar", jdbcTemplate.queryForObject("SELECT DATA_TYPE FROM information_schema.COLUMNS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'profile_languages' AND COLUMN_NAME = 'level'", String.class));
		assertEquals(20, jdbcTemplate.queryForObject("SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'profile_languages' AND COLUMN_NAME = 'level'", Integer.class));
		assertEquals(1, constraintCount("uk_profile_languages_profile_language"));
		assertEquals(1, constraintCount("fk_profile_languages_profile"));
		assertEquals(1, constraintCount("fk_profile_languages_language"));
		assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS "
				+ "WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME = 'chk_profile_languages_level'", Integer.class));

		UserAccount user = saveUser();
		Profile profile = saveProfile(user, "Constraint Profile");
		Language language = languageRepository.saveAndFlush(new Language("constraint-" + UUID.randomUUID()));
		insertAssociation(profile.getId(), language.getId(), "NATIVE");
		assertDatabaseIntegrityViolation(() -> insertAssociation(profile.getId(), language.getId(), "NATIVE"));
		assertDatabaseIntegrityViolation(() -> insertAssociation(profile.getId(), language.getId(), "FLUENT"));
		assertDatabaseIntegrityViolation(() -> insertAssociation(Long.MAX_VALUE, language.getId(), "NATIVE"));
		assertDatabaseIntegrityViolation(() -> insertAssociation(profile.getId(), Long.MAX_VALUE, "NATIVE"));
	}

	@Test
	void persistsCrudInvalidatesPreviewAndReturnsProfileVersions() throws Exception {
		UserAccount user = saveUser();
		Profile profile = saveProfile(user, "Language Profile");
		Language english = findSeedLanguage("English");
		Language vietnamese = findSeedLanguage("Vietnamese");

		ReflectionTestUtils.setField(profile, "hasPreviewed", true);
		profileRepository.saveAndFlush(profile);
		String createResponse = mockMvc.perform(post("/api/profiles/{id}/languages", profile.getId())
				.with(authentication(userPrincipal(user))).contentType(MediaType.APPLICATION_JSON)
				.content(requestJson(english.getId(), " native ", 0)))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.data.profileVersion").value(1))
				.andExpect(jsonPath("$.data.profileLanguage.languageId").value(english.getId()))
				.andExpect(jsonPath("$.data.profileLanguage.languageName").value("English"))
				.andExpect(jsonPath("$.data.profileLanguage.level").value("NATIVE"))
				.andReturn().getResponse().getContentAsString();
		Long profileLanguageId = ((Number) com.jayway.jsonpath.JsonPath.read(createResponse,
				"$.data.profileLanguage.profileLanguageId")).longValue();

		Profile afterCreate = profileRepository.findById(profile.getId()).orElseThrow();
		assertEquals(1L, afterCreate.getVersion());
		assertFalse(afterCreate.isHasPreviewed());
		assertEquals(english.getId(), profileLanguageRepository.findById(profileLanguageId).orElseThrow().getLanguage().getId());

		jdbcTemplate.update("UPDATE profiles SET has_previewed = TRUE WHERE id = ?", profile.getId());
		mockMvc.perform(put("/api/profiles/{profileId}/languages/{profileLanguageId}", profile.getId(), profileLanguageId)
				.with(authentication(userPrincipal(user))).contentType(MediaType.APPLICATION_JSON)
				.content(requestJson(vietnamese.getId(), " advanced ", 1)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.profileVersion").value(2))
				.andExpect(jsonPath("$.data.profileLanguage.languageId").value(vietnamese.getId()))
				.andExpect(jsonPath("$.data.profileLanguage.level").value("ADVANCED"));
		ProfileLanguage updated = profileLanguageRepository.findById(profileLanguageId).orElseThrow();
		assertEquals(vietnamese.getId(), updated.getLanguage().getId());
		assertEquals(LanguageLevel.ADVANCED, updated.getLevel());
		assertFalse(profileRepository.findById(profile.getId()).orElseThrow().isHasPreviewed());

		mockMvc.perform(get("/api/profiles/{id}/languages", profile.getId())
				.with(authentication(userPrincipal(user)))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].languageName").value("Vietnamese"));

		jdbcTemplate.update("UPDATE profiles SET has_previewed = TRUE WHERE id = ?", profile.getId());
		mockMvc.perform(delete("/api/profiles/{profileId}/languages/{profileLanguageId}", profile.getId(), profileLanguageId)
				.with(authentication(userPrincipal(user))).contentType(MediaType.APPLICATION_JSON)
				.content("{\"version\":2}" )).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.profileVersion").value(3));
		assertTrue(profileLanguageRepository.findById(profileLanguageId).isEmpty());
		assertEquals(3L, profileRepository.findById(profile.getId()).orElseThrow().getVersion());
		assertFalse(profileRepository.findById(profile.getId()).orElseThrow().isHasPreviewed());
	}

	@Test
	void listsInApprovedProficiencyAndCaseInsensitiveNameOrder() throws Exception {
		UserAccount user = saveUser();
		Profile profile = saveProfile(user, "Ordering Profile");
		Language english = findSeedLanguage("English");
		Language vietnamese = findSeedLanguage("Vietnamese");
		Language japanese = findSeedLanguage("Japanese");
		String marker = "ordering-" + UUID.randomUUID();
		Language zulu = languageRepository.saveAndFlush(new Language(marker + "-zulu"));
		Language alpha = languageRepository.saveAndFlush(new Language(marker + "-Alpha"));

		profileLanguageRepository.saveAndFlush(new ProfileLanguage(profile, english, LanguageLevel.BEGINNER));
		profileLanguageRepository.saveAndFlush(new ProfileLanguage(profile, vietnamese, LanguageLevel.INTERMEDIATE));
		profileLanguageRepository.saveAndFlush(new ProfileLanguage(profile, japanese, LanguageLevel.NATIVE));
		profileLanguageRepository.saveAndFlush(new ProfileLanguage(profile, zulu, LanguageLevel.ADVANCED));
		profileLanguageRepository.saveAndFlush(new ProfileLanguage(profile, alpha, LanguageLevel.ADVANCED));

		mockMvc.perform(get("/api/profiles/{id}/languages", profile.getId())
				.with(authentication(userPrincipal(user)))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].languageName").value("Japanese"))
				.andExpect(jsonPath("$.data[1].languageName").value(marker + "-Alpha"))
				.andExpect(jsonPath("$.data[2].languageName").value(marker + "-zulu"))
				.andExpect(jsonPath("$.data[3].languageName").value("Vietnamese"))
				.andExpect(jsonPath("$.data[4].languageName").value("English"));
	}

	@Test
	void scopesOwnershipActiveProfilesAndVersions() {
		UserAccount owner = saveUser();
		UserAccount foreignUser = saveUser();
		Profile ownerProfile = saveProfile(owner, "Owner Language Profile");
		Profile foreignProfile = saveProfile(foreignUser, "Foreign Language Profile");
		Language english = findSeedLanguage("English");
		ProfileLanguage foreignAssociation = profileLanguageRepository
				.saveAndFlush(new ProfileLanguage(foreignProfile, english, LanguageLevel.NATIVE));

		ApiException foreign = assertThrows(ApiException.class,
				() -> profileLanguageService.delete(owner.getId(), ownerProfile.getId(), foreignAssociation.getId(), 0L));
		assertEquals(ErrorCode.PROFILE_LANGUAGE_NOT_FOUND, foreign.getErrorCode());

		profileLanguageService.create(owner.getId(), ownerProfile.getId(),
				new ProfileLanguageRequest(english.getId(), "BEGINNER", 0L));
		ApiException duplicate = assertThrows(ApiException.class, () -> profileLanguageService.create(owner.getId(),
				ownerProfile.getId(), new ProfileLanguageRequest(english.getId(), "ADVANCED", 1L)));
		assertEquals(ErrorCode.PROFILE_LANGUAGE_ALREADY_EXISTS, duplicate.getErrorCode());

		ApiException stale = assertThrows(ApiException.class, () -> profileLanguageService.create(owner.getId(),
				ownerProfile.getId(), new ProfileLanguageRequest(findSeedLanguage("Vietnamese").getId(), "NATIVE", 0L)));
		assertEquals(ErrorCode.PROFILE_VERSION_CONFLICT, stale.getErrorCode());

		Profile deleted = profileRepository.findById(ownerProfile.getId()).orElseThrow();
		deleted.softDelete(java.time.Instant.now());
		profileRepository.saveAndFlush(deleted);
		ApiException inactive = assertThrows(ApiException.class,
				() -> profileLanguageService.list(owner.getId(), ownerProfile.getId()));
		assertEquals(ErrorCode.PROFILE_NOT_FOUND, inactive.getErrorCode());
	}

	private int constraintCount(String constraintName) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS "
				+ "WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'profile_languages' AND CONSTRAINT_NAME = ?",
				Integer.class, constraintName);
	}

	private void insertAssociation(long profileId, long languageId, String level) {
		jdbcTemplate.update("INSERT INTO profile_languages (profile_id, language_id, `level`, created_at, updated_at) "
				+ "VALUES (?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))", profileId, languageId, level);
	}

	private void assertDatabaseIntegrityViolation(Runnable action) {
		assertThrows(DataAccessException.class, action::run);
	}

	private UserAccount saveUser() {
		return userRepository.saveAndFlush(new UserAccount(UUID.randomUUID() + "@example.com",
				"language-member-" + UUID.randomUUID(), "hash", UserRole.MEMBER, UserStatus.ACTIVE));
	}

	private Profile saveProfile(UserAccount user, String name) {
		return profileRepository.saveAndFlush(new Profile(user, name, "First", "Last", "Engineer", BigDecimal.ONE,
				"Personality", "Summary"));
	}

	private Language findSeedLanguage(String name) {
		return languageRepository.findAll().stream().filter(language -> name.equals(language.getName())).findFirst()
				.orElseThrow();
	}

	private Authentication userPrincipal(UserAccount user) {
		return new UsernamePasswordAuthenticationToken(
				new UserPrincipal(user.getId(), user.getEmail(), user.getUsername(), user.getRole()), null, List.of());
	}

	private String requestJson(Long languageId, String level, long version) {
		return "{\"languageId\":" + languageId + ",\"level\":\"" + level + "\",\"version\":" + version + "}";
	}
}
