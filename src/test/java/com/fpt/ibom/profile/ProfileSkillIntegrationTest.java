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
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
import com.fpt.ibom.master.entity.Skill;
import com.fpt.ibom.master.entity.SkillCategory;
import com.fpt.ibom.master.repository.SkillCategoryRepository;
import com.fpt.ibom.master.repository.SkillRepository;
import com.fpt.ibom.profile.dto.ProfileSkillRequest;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.entity.ProfileSkill;
import com.fpt.ibom.profile.repository.ProfileRepository;
import com.fpt.ibom.profile.repository.ProfileSkillRepository;
import com.fpt.ibom.profile.service.ProfileSkillService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Import(ProfileSkillIntegrationTest.FixedClockConfiguration.class)
class ProfileSkillIntegrationTest extends MySqlIntegrationTest {

	private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
	private static final Instant FIXED_INSTANT = Instant.parse("2026-09-10T18:30:00Z");
	private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 9, 11);

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ProfileSkillService profileSkillService;
	@Autowired
	private ProfileSkillRepository profileSkillRepository;
	@Autowired
	private ProfileRepository profileRepository;
	@Autowired
	private SkillRepository skillRepository;
	@Autowired
	private SkillCategoryRepository skillCategoryRepository;
	@Autowired
	private UserAccountRepository userRepository;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@Order(1)
	void migrationCreatesProfileSkillSchemaAndApprovedConstraints() {
		assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'profile_skills'", Integer.class));
		assertEquals("decimal", jdbcTemplate.queryForObject("SELECT DATA_TYPE FROM information_schema.COLUMNS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'profile_skills' AND COLUMN_NAME = 'experience_years'",
				String.class));
		assertEquals(5, jdbcTemplate.queryForObject("SELECT NUMERIC_PRECISION FROM information_schema.COLUMNS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'profile_skills' AND COLUMN_NAME = 'experience_years'",
				Integer.class));
		assertEquals(2, jdbcTemplate.queryForObject("SELECT NUMERIC_SCALE FROM information_schema.COLUMNS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'profile_skills' AND COLUMN_NAME = 'experience_years'",
				Integer.class));
		assertEquals("date", jdbcTemplate.queryForObject("SELECT DATA_TYPE FROM information_schema.COLUMNS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'profile_skills' AND COLUMN_NAME = 'last_used'",
				String.class));
		assertEquals(1, constraintCount("uk_profile_skills_profile_skill"));
		assertEquals(1, constraintCount("fk_profile_skills_profile"));
		assertEquals(1, constraintCount("fk_profile_skills_skill"));
		assertEquals(1, constraintCount("chk_profile_skills_experience_years"));
		assertEquals(1, indexCount("idx_profile_skills_profile_id"));
		assertEquals(1, indexCount("idx_profile_skills_skill_id"));
		assertEquals("NO", nullable("profile_id"));
		assertEquals("NO", nullable("skill_id"));
		assertEquals("NO", nullable("experience_years"));
		assertEquals("YES", nullable("last_used"));
		assertEquals(0, columnCount("created_at"));
		assertEquals(0, columnCount("updated_at"));
		assertEquals(0, columnCount("seniority_id"));
		assertEquals(0, columnCount("deleted_at"));
	}

	@Test
	@Order(2)
	void persistsCrudInvalidatesPreviewReturnsVersionsAndPreservesSkillMaster() throws Exception {
		UserAccount user = saveUser();
		Profile profile = saveProfile(user, "Skill CRUD Profile");
		Skill first = saveSkill("Java-" + UUID.randomUUID(), "BACKEND");
		Skill replacement = saveSkill("Spring-" + UUID.randomUUID(), "BACKEND");
		ReflectionTestUtils.setField(profile, "hasPreviewed", true);
		profileRepository.saveAndFlush(profile);

		String createResponse = mockMvc.perform(post("/api/profiles/{id}/skills", profile.getId())
				.with(authentication(userPrincipal(user))).contentType(MediaType.APPLICATION_JSON)
				.content(requestJson(first.getId(), "2.75", "2025-01-15", 0)))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.data.profileVersion").value(1))
				.andExpect(jsonPath("$.data.profileSkill.skillId").value(first.getId()))
				.andExpect(jsonPath("$.data.profileSkill.skillName").value(first.getName()))
				.andExpect(jsonPath("$.data.profileSkill.categoryCode").value("BACKEND"))
				.andExpect(jsonPath("$.data.profileSkill.experienceYears").value(2.75))
				.andExpect(jsonPath("$.data.profileSkill.lastUsed").value("2025-01-15"))
				.andReturn().getResponse().getContentAsString();
		Long profileSkillId = ((Number) com.jayway.jsonpath.JsonPath.read(createResponse,
				"$.data.profileSkill.profileSkillId")).longValue();

		ProfileSkill created = profileSkillRepository.findById(profileSkillId).orElseThrow();
		assertNotNull(created.getExperienceYears());
		assertEquals(new BigDecimal("2.75"), created.getExperienceYears());
		assertEquals(LocalDate.of(2025, 1, 15), created.getLastUsed());
		assertFalse(profileRepository.findById(profile.getId()).orElseThrow().isHasPreviewed());

		jdbcTemplate.update("UPDATE profiles SET has_previewed = TRUE WHERE id = ?", profile.getId());
		mockMvc.perform(put("/api/profiles/{profileId}/skills/{profileSkillId}", profile.getId(), profileSkillId)
				.with(authentication(userPrincipal(user))).contentType(MediaType.APPLICATION_JSON)
				.content(requestJson(replacement.getId(), "4.25", null, 1)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.profileVersion").value(2))
				.andExpect(jsonPath("$.data.profileSkill.skillId").value(replacement.getId()))
				.andExpect(jsonPath("$.data.profileSkill.experienceYears").value(4.25))
				.andExpect(jsonPath("$.data.profileSkill.lastUsed").doesNotExist());
		ProfileSkill updated = profileSkillRepository.findById(profileSkillId).orElseThrow();
		assertEquals(replacement.getId(), updated.getSkill().getId());
		assertEquals(new BigDecimal("4.25"), updated.getExperienceYears());
		assertEquals(null, updated.getLastUsed());
		assertFalse(profileRepository.findById(profile.getId()).orElseThrow().isHasPreviewed());

		mockMvc.perform(get("/api/profiles/{id}/skills", profile.getId())
				.with(authentication(userPrincipal(user)))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].skillId").value(replacement.getId()));

		jdbcTemplate.update("UPDATE profiles SET has_previewed = TRUE WHERE id = ?", profile.getId());
		mockMvc.perform(delete("/api/profiles/{profileId}/skills/{profileSkillId}", profile.getId(), profileSkillId)
				.with(authentication(userPrincipal(user))).contentType(MediaType.APPLICATION_JSON)
				.content("{\"version\":2}" )).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.profileVersion").value(3));
		assertTrue(profileSkillRepository.findById(profileSkillId).isEmpty());
		assertTrue(skillRepository.findById(first.getId()).isPresent());
		assertTrue(skillRepository.findById(replacement.getId()).isPresent());
		assertEquals(3L, profileRepository.findById(profile.getId()).orElseThrow().getVersion());
		assertFalse(profileRepository.findById(profile.getId()).orElseThrow().isHasPreviewed());
	}

	@Test
	@Order(3)
	void listsByExperienceDescendingThenCaseInsensitiveSkillName() throws Exception {
		UserAccount user = saveUser();
		Profile profile = saveProfile(user, "Skill Ordering Profile");
		String marker = "ordering-" + UUID.randomUUID();
		Skill zulu = saveSkill(marker + "-Zulu", "BACKEND");
		Skill alpha = saveSkill(marker + "-Alpha", "BACKEND");
		Skill lower = saveSkill(marker + "-beta", "BACKEND");
		Skill older = saveSkill(marker + "-NewestName", "BACKEND");
		profileSkillRepository.saveAndFlush(new ProfileSkill(profile, zulu, new BigDecimal("3.50"), null));
		profileSkillRepository.saveAndFlush(new ProfileSkill(profile, alpha, new BigDecimal("3.50"), null));
		profileSkillRepository.saveAndFlush(new ProfileSkill(profile, lower, new BigDecimal("3.50"), null));
		profileSkillRepository.saveAndFlush(new ProfileSkill(profile, older, new BigDecimal("2.00"), null));

		mockMvc.perform(get("/api/profiles/{id}/skills", profile.getId())
				.with(authentication(userPrincipal(user)))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].skillName").value(alpha.getName()))
				.andExpect(jsonPath("$.data[1].skillName").value(lower.getName()))
				.andExpect(jsonPath("$.data[2].skillName").value(zulu.getName()))
				.andExpect(jsonPath("$.data[3].skillName").value(older.getName()));
	}

	@Test
	@Order(4)
	void enforcesAssociationConstraintsAndAllowsCrossProfileSkillReuse() {
		UserAccount firstUser = saveUser();
		UserAccount secondUser = saveUser();
		Profile firstProfile = saveProfile(firstUser, "First Skill Profile");
		Profile secondProfile = saveProfile(secondUser, "Second Skill Profile");
		Skill skill = saveSkill("constraint-" + UUID.randomUUID(), "BACKEND");

		insertAssociation(firstProfile.getId(), skill.getId(), "1.25");
		assertDatabaseIntegrityViolation(() -> insertAssociation(firstProfile.getId(), skill.getId(), "2.00"));
		assertDatabaseIntegrityViolation(() -> insertAssociation(Long.MAX_VALUE, skill.getId(), "1.00"));
		assertDatabaseIntegrityViolation(() -> insertAssociation(firstProfile.getId(), Long.MAX_VALUE, "1.00"));
		assertDatabaseIntegrityViolation(() -> insertAssociation(secondProfile.getId(), skill.getId(), "-0.01"));

		ProfileSkill reused = profileSkillRepository.findByProfileId(firstProfile.getId()).get(0);
		ProfileSkill secondAssociation = profileSkillRepository.saveAndFlush(
				new ProfileSkill(secondProfile, skill, new BigDecimal("2.50"), null));
		assertNotNull(secondAssociation.getId());
		ApiException duplicate = assertThrows(ApiException.class, () -> profileSkillService.create(firstUser.getId(),
				firstProfile.getId(), new ProfileSkillRequest(skill.getId(), new BigDecimal("3.00"), null, 0L)));
		assertEquals(ErrorCode.PROFILE_SKILL_ALREADY_EXISTS, duplicate.getErrorCode());
		assertEquals(skill.getId(), reused.getSkill().getId());
	}

	@Test
	@Order(5)
	void scopesOwnershipActiveProfilesAndVersionsAndRejectsFutureDates() {
		UserAccount owner = saveUser();
		UserAccount foreignUser = saveUser();
		Profile ownerProfile = saveProfile(owner, "Owner Skill Profile");
		Profile foreignProfile = saveProfile(foreignUser, "Foreign Skill Profile");
		Skill skill = saveSkill("ownership-" + UUID.randomUUID(), "BACKEND");
		ProfileSkill foreignAssociation = profileSkillRepository.saveAndFlush(
				new ProfileSkill(foreignProfile, skill, BigDecimal.ONE, null));

		ApiException foreign = assertThrows(ApiException.class,
				() -> profileSkillService.delete(owner.getId(), ownerProfile.getId(), foreignAssociation.getId(), 0L));
		assertEquals(ErrorCode.PROFILE_SKILL_NOT_FOUND, foreign.getErrorCode());

		ApiException future = assertThrows(ApiException.class, () -> profileSkillService.create(owner.getId(),
				ownerProfile.getId(), new ProfileSkillRequest(skill.getId(), BigDecimal.ONE, BUSINESS_DATE.plusDays(1), 0L)));
		assertEquals(ErrorCode.PROFILE_SKILL_LAST_USED_IN_FUTURE, future.getErrorCode());

		profileSkillService.create(owner.getId(), ownerProfile.getId(),
				new ProfileSkillRequest(skill.getId(), new BigDecimal("1.50"), null, 0L));
		ApiException stale = assertThrows(ApiException.class, () -> profileSkillService.create(owner.getId(), ownerProfile.getId(),
				new ProfileSkillRequest(saveSkill("stale-" + UUID.randomUUID(), "BACKEND").getId(), BigDecimal.ONE, null, 0L)));
		assertEquals(ErrorCode.PROFILE_VERSION_CONFLICT, stale.getErrorCode());

		Profile deleted = profileRepository.findById(ownerProfile.getId()).orElseThrow();
		deleted.softDelete(Instant.now());
		profileRepository.saveAndFlush(deleted);
		ApiException inactive = assertThrows(ApiException.class,
				() -> profileSkillService.list(owner.getId(), ownerProfile.getId()));
		assertEquals(ErrorCode.PROFILE_NOT_FOUND, inactive.getErrorCode());
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClockConfiguration {

		@Bean("fixedClock")
		@Primary
		Clock fixedClock() {
			return Clock.fixed(FIXED_INSTANT, BUSINESS_ZONE);
		}
	}

	private int constraintCount(String constraintName) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS "
				+ "WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'profile_skills' AND CONSTRAINT_NAME = ?",
				Integer.class, constraintName);
	}

	private int indexCount(String indexName) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.STATISTICS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'profile_skills' AND INDEX_NAME = ?", Integer.class,
				indexName);
	}

	private String nullable(String columnName) {
		return jdbcTemplate.queryForObject("SELECT IS_NULLABLE FROM information_schema.COLUMNS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'profile_skills' AND COLUMN_NAME = ?", String.class,
				columnName);
	}

	private int columnCount(String columnName) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.COLUMNS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'profile_skills' AND COLUMN_NAME = ?", Integer.class,
				columnName);
	}

	private void insertAssociation(long profileId, long skillId, String experienceYears) {
		jdbcTemplate.update("INSERT INTO profile_skills (profile_id, skill_id, experience_years, last_used) "
				+ "VALUES (?, ?, ?, NULL)", profileId, skillId, experienceYears);
	}

	private void assertDatabaseIntegrityViolation(Runnable action) {
		assertThrows(DataAccessException.class, action::run);
	}

	private UserAccount saveUser() {
		return userRepository.saveAndFlush(new UserAccount(UUID.randomUUID() + "@example.com",
				"profile-skill-member-" + UUID.randomUUID(), "hash", UserRole.MEMBER, UserStatus.ACTIVE));
	}

	private Profile saveProfile(UserAccount user, String name) {
		return profileRepository.saveAndFlush(new Profile(user, name, "First", "Last", "Engineer", BigDecimal.ONE,
				"Personality", "Summary"));
	}

	private Skill saveSkill(String name, String categoryCode) {
		SkillCategory category = skillCategoryRepository.findByCode(categoryCode).orElseThrow();
		return skillRepository.saveAndFlush(new Skill(name, category));
	}

	private Authentication userPrincipal(UserAccount user) {
		return new UsernamePasswordAuthenticationToken(
				new UserPrincipal(user.getId(), user.getEmail(), user.getUsername(), user.getRole()), null, List.of());
	}

	private String requestJson(Long skillId, String experienceYears, String lastUsed, long version) {
		String date = lastUsed == null ? "null" : "\"" + lastUsed + "\"";
		return "{\"skillId\":" + skillId + ",\"experienceYears\":" + experienceYears + ",\"lastUsed\":" + date
				+ ",\"version\":" + version + "}";
	}
}
