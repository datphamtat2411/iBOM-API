package com.fpt.ibom.master;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.fpt.ibom.MySqlIntegrationTest;
import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.master.entity.Skill;
import com.fpt.ibom.master.entity.SkillCategory;
import com.fpt.ibom.master.repository.SkillCategoryRepository;
import com.fpt.ibom.master.repository.SkillRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SkillIntegrationTest extends MySqlIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private SkillRepository skillRepository;

	@Autowired
	private SkillCategoryRepository skillCategoryRepository;

	@Autowired
	private UserAccountRepository userRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void migrationCreatesSkillSchemaAndApprovedCategorySeedRows() {
		assertTrue(jdbcTemplate.queryForList("SELECT TABLE_NAME FROM information_schema.TABLES "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('skill_categories', 'skills')",
				String.class).containsAll(List.of("skill_categories", "skills")));
		assertEquals("NO", jdbcTemplate.queryForObject("SELECT IS_NULLABLE FROM information_schema.COLUMNS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'skills' AND COLUMN_NAME = 'name'", String.class));
		assertEquals("NO", jdbcTemplate.queryForObject("SELECT IS_NULLABLE FROM information_schema.COLUMNS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'skills' AND COLUMN_NAME = 'category_id'", String.class));
		assertEquals(6, jdbcTemplate.queryForObject("SELECT DATETIME_PRECISION FROM information_schema.COLUMNS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'skills' AND COLUMN_NAME = 'created_at'", Integer.class));
		assertEquals("STORED GENERATED", jdbcTemplate.queryForObject("SELECT EXTRA FROM information_schema.COLUMNS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'skills' AND COLUMN_NAME = 'name_ci'", String.class));
		assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.STATISTICS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'skills' AND INDEX_NAME = 'idx_skills_category_id'",
				Integer.class));

		for (CategorySeed seed : List.of(
				new CategorySeed("PROGRAMMING_LANGUAGE", "Programming Language"),
				new CategorySeed("FRONTEND", "Frontend"),
				new CategorySeed("BACKEND", "Backend"),
				new CategorySeed("MOBILE_GAME", "Mobile & Game"),
				new CategorySeed("DATABASE_DATA", "Database & Data"),
				new CategorySeed("CLOUD_DEVOPS", "Cloud & DevOps"),
				new CategorySeed("API_MESSAGING_TESTING", "API, Messaging & Testing"))) {
			MapRow row = jdbcTemplate.queryForObject("SELECT id FROM skill_categories WHERE code = ? AND name = ?",
				(rs, rowNum) -> new MapRow(rs.getLong("id")), seed.code(), seed.name());
			assertNotNull(row);
			assertTrue(row.id() > 0);
		}

		for (Constraint constraint : List.of(
				new Constraint("skill_categories", "uk_skill_categories_code"),
				new Constraint("skill_categories", "uk_skill_categories_name"),
				new Constraint("skills", "uk_skills_name_ci"),
				new Constraint("skills", "chk_skills_name_not_blank"),
				new Constraint("skills", "fk_skills_category"))) {
			assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS "
					+ "WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = ? AND CONSTRAINT_NAME = ?",
					Integer.class, constraint.table(), constraint.name()));
		}
	}

	@Test
	void databaseEnforcesTrimmedNamesCaseInsensitiveUniquenessAndCategoryForeignKey() {
		SkillCategory category = skillCategoryRepository.findByCode("BACKEND").orElseThrow();
		String marker = "skill-" + UUID.randomUUID();

		Skill trimmed = skillRepository.saveAndFlush(new Skill("  " + marker + "  ", category));
		assertEquals(marker, trimmed.getName());
		assertDatabaseIntegrityViolation(() -> skillRepository.saveAndFlush(
				new Skill(marker.toUpperCase(Locale.ROOT), category)));
		assertDatabaseIntegrityViolation(() -> skillRepository.saveAndFlush(new Skill("   ", category)));
		assertDatabaseIntegrityViolation(() -> skillRepository.saveAndFlush(new Skill(null, category)));
		assertDatabaseIntegrityViolation(() -> jdbcTemplate.update(
				"INSERT INTO skills (name, category_id, created_at, updated_at) VALUES (?, ?, ?, ?)",
				marker + "-untrimmed", category.getId() + 999999L,
				Timestamp.from(Instant.now()), Timestamp.from(Instant.now())));
	}

	@Test
	void skillReadRouteSearchesOrdersPaginatesAndReturnsCategoryFieldsAndTimestamps() throws Exception {
		SkillCategory category = skillCategoryRepository.findByCode("BACKEND").orElseThrow();
		String marker = UUID.randomUUID().toString().substring(0, 8);
		String prefix = "skill-" + marker;
		skillRepository.saveAndFlush(new Skill(prefix + "-Zulu", category));
		skillRepository.saveAndFlush(new Skill(prefix + "-Alpha", category));
		skillRepository.saveAndFlush(new Skill(prefix + "-Mike", category));
		UserAccount user = saveUser();

		mockMvc.perform(get("/api/master/skills").param("page", "0").param("size", "2")
				.param("search", prefix.toUpperCase(Locale.ROOT)).with(authentication(userPrincipal(user))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.page").value(0))
				.andExpect(jsonPath("$.data.size").value(2))
				.andExpect(jsonPath("$.data.totalElements").value(3))
				.andExpect(jsonPath("$.data.totalPages").value(2))
				.andExpect(jsonPath("$.data.content[0].name").value(prefix + "-Alpha"))
				.andExpect(jsonPath("$.data.content[1].name").value(prefix + "-Mike"))
				.andExpect(jsonPath("$.data.content[0].categoryId").value(category.getId()))
				.andExpect(jsonPath("$.data.content[0].categoryCode").value("BACKEND"))
				.andExpect(jsonPath("$.data.content[0].categoryName").value("Backend"))
				.andExpect(jsonPath("$.data.content[0].createdAt").isNotEmpty())
				.andExpect(jsonPath("$.data.content[0].updatedAt").isNotEmpty());

		mockMvc.perform(get("/api/master/skills").param("page", "1").param("size", "2")
				.param("search", "  " + prefix + "  ").with(authentication(userPrincipal(user))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[0].name").value(prefix + "-Zulu"));

		mockMvc.perform(get("/api/master/skills").param("size", "0")
				.with(authentication(userPrincipal(user))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
	}

	private UserAccount saveUser() {
		return userRepository.saveAndFlush(new UserAccount(UUID.randomUUID() + "@example.com",
				"skill-user-" + UUID.randomUUID(), "hash", UserRole.MEMBER, UserStatus.ACTIVE));
	}

	private Authentication userPrincipal(UserAccount user) {
		return new UsernamePasswordAuthenticationToken(
				new UserPrincipal(user.getId(), user.getEmail(), user.getUsername(), user.getRole()), null, List.of());
	}

	private void assertDatabaseIntegrityViolation(Runnable action) {
		RuntimeException exception = assertThrows(RuntimeException.class, action::run);
		assertTrue(exception instanceof DataIntegrityViolationException || exception instanceof JpaSystemException);
	}

	private record CategorySeed(String code, String name) {
	}

	private record Constraint(String table, String name) {
	}

	private record MapRow(long id) {
	}
}
