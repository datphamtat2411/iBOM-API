package com.fpt.ibom.master;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import com.fpt.ibom.profile.dto.ProfileSkillResponse;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.entity.ProfileSkill;
import com.fpt.ibom.profile.repository.ProfileRepository;
import com.fpt.ibom.profile.repository.ProfileSkillRepository;
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
	private ProfileRepository profileRepository;

	@Autowired
	private ProfileSkillRepository profileSkillRepository;

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
	void authenticatedCategoryReadReturnsAllSeededCategoriesInSeedOrder() throws Exception {
		UserAccount member = saveUser(UserRole.MEMBER);

		mockMvc.perform(get("/api/master/skill-categories").with(authentication(userPrincipal(member))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(7))
				.andExpect(jsonPath("$.data[0].code").value("PROGRAMMING_LANGUAGE"))
				.andExpect(jsonPath("$.data[0].name").value("Programming Language"))
				.andExpect(jsonPath("$.data[1].code").value("FRONTEND"))
				.andExpect(jsonPath("$.data[1].name").value("Frontend"))
				.andExpect(jsonPath("$.data[2].code").value("BACKEND"))
				.andExpect(jsonPath("$.data[2].name").value("Backend"))
				.andExpect(jsonPath("$.data[3].code").value("MOBILE_GAME"))
				.andExpect(jsonPath("$.data[3].name").value("Mobile & Game"))
				.andExpect(jsonPath("$.data[4].code").value("DATABASE_DATA"))
				.andExpect(jsonPath("$.data[4].name").value("Database & Data"))
				.andExpect(jsonPath("$.data[5].code").value("CLOUD_DEVOPS"))
				.andExpect(jsonPath("$.data[5].name").value("Cloud & DevOps"))
				.andExpect(jsonPath("$.data[6].code").value("API_MESSAGING_TESTING"))
				.andExpect(jsonPath("$.data[6].name").value("API, Messaging & Testing"));
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

	@Test
	void skillReadRouteFiltersByCategoryAndSearchBeforePagination() throws Exception {
		SkillCategory selectedCategory = skillCategoryRepository.findByCode("API_MESSAGING_TESTING").orElseThrow();
		SkillCategory otherCategory = skillCategoryRepository.findByCode("BACKEND").orElseThrow();
		String marker = "category-filter-" + UUID.randomUUID();
		skillRepository.saveAndFlush(new Skill(marker + "-Zulu", selectedCategory));
		skillRepository.saveAndFlush(new Skill(marker + "-Alpha", selectedCategory));
		skillRepository.saveAndFlush(new Skill(marker + "-Other", otherCategory));
		UserAccount user = saveUser();

		mockMvc.perform(get("/api/master/skills").param("page", "0").param("size", "1")
				.param("categoryId", selectedCategory.getId().toString()).with(authentication(userPrincipal(user))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.page").value(0))
				.andExpect(jsonPath("$.data.size").value(1))
				.andExpect(jsonPath("$.data.totalElements").value(2))
				.andExpect(jsonPath("$.data.totalPages").value(2))
				.andExpect(jsonPath("$.data.content[0].name").value(marker + "-Alpha"))
				.andExpect(jsonPath("$.data.content[0].categoryId").value(selectedCategory.getId()))
				.andExpect(jsonPath("$.data.content[0].categoryCode").value("API_MESSAGING_TESTING"));

		mockMvc.perform(get("/api/master/skills").param("size", "10")
				.param("search", marker.toUpperCase(Locale.ROOT))
				.param("categoryId", selectedCategory.getId().toString()).with(authentication(userPrincipal(user))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(2))
				.andExpect(jsonPath("$.data.content.length()").value(2))
				.andExpect(jsonPath("$.data.content[0].name").value(marker + "-Alpha"))
				.andExpect(jsonPath("$.data.content[1].name").value(marker + "-Zulu"))
				.andExpect(jsonPath("$.data.content[0].categoryId").value(selectedCategory.getId()))
				.andExpect(jsonPath("$.data.content[1].categoryId").value(selectedCategory.getId()));

		mockMvc.perform(get("/api/master/skills").param("search", "Other")
				.param("categoryId", selectedCategory.getId().toString()).with(authentication(userPrincipal(user))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content").isEmpty())
				.andExpect(jsonPath("$.data.totalElements").value(0))
				.andExpect(jsonPath("$.data.totalPages").value(0));

		mockMvc.perform(get("/api/master/skills").param("categoryId", "999999999")
				.with(authentication(userPrincipal(user))))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("SKILL_CATEGORY_NOT_FOUND"));
	}

	@Test
	void managerCanCreateUpdateAndDeleteSkillThroughMigratedDatabase() throws Exception {
		SkillCategory backend = skillCategoryRepository.findByCode("BACKEND").orElseThrow();
		SkillCategory database = skillCategoryRepository.findByCode("DATABASE_DATA").orElseThrow();
		UserAccount manager = saveUser(UserRole.MANAGER);
		String marker = "crud-" + UUID.randomUUID();

		String response = mockMvc.perform(post("/api/master/skills").with(authentication(userPrincipal(manager)))
				.contentType(APPLICATION_JSON).content("{\"name\":\"  " + marker + "  \",\"categoryId\":"
					+ backend.getId() + "}"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.data.name").value(marker))
				.andExpect(jsonPath("$.data.categoryCode").value("BACKEND")).andReturn().getResponse()
				.getContentAsString();
		Number skillIdValue = com.jayway.jsonpath.JsonPath.read(response, "$.data.id");
		long skillId = skillIdValue.longValue();

		mockMvc.perform(put("/api/master/skills/" + skillId).with(authentication(userPrincipal(manager)))
				.contentType(APPLICATION_JSON).content("{\"name\":\"  " + marker + "-updated  \",\"categoryId\":"
					+ database.getId() + "}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value(marker + "-updated"))
				.andExpect(jsonPath("$.data.categoryCode").value("DATABASE_DATA"));
		assertEquals(marker + "-updated", skillRepository.findById(skillId).orElseThrow().getName());

		mockMvc.perform(post("/api/master/skills").with(authentication(userPrincipal(manager)))
				.contentType(APPLICATION_JSON).content("{\"name\":\"" + marker.toUpperCase(Locale.ROOT)
					+ "-UPDATED\",\"categoryId\":" + backend.getId() + "}"))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("SKILL_NAME_ALREADY_EXISTS"));
		mockMvc.perform(post("/api/master/skills").with(authentication(userPrincipal(manager)))
				.contentType(APPLICATION_JSON).content("{\"name\":\"unknown-category\",\"categoryId\":999999999}"))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("SKILL_CATEGORY_NOT_FOUND"));
		mockMvc.perform(put("/api/master/skills/999999999").with(authentication(userPrincipal(manager)))
				.contentType(APPLICATION_JSON).content("{\"name\":\"unknown-skill\",\"categoryId\":4}"))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("SKILL_NOT_FOUND"));

		mockMvc.perform(delete("/api/master/skills/" + skillId).with(authentication(userPrincipal(manager))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
				.andExpect(jsonPath("$.data").doesNotExist());
		assertTrue(skillRepository.findById(skillId).isEmpty());
	}

	@Test
	void rejectsDeletingReferencedSkillAndPreservesProfileSkillData() throws Exception {
		SkillCategory category = skillCategoryRepository.findByCode("BACKEND").orElseThrow();
		UserAccount manager = saveUser(UserRole.MANAGER);
		UserAccount member = saveUser(UserRole.MEMBER);
		Skill skill = skillRepository.saveAndFlush(new Skill("referenced-" + UUID.randomUUID(), category));
		Profile profile = profileRepository.saveAndFlush(new Profile(member, "skill-profile-" + UUID.randomUUID(), "First",
				"Last", "Engineer", BigDecimal.ONE, null, null));
		ProfileSkill profileSkill = profileSkillRepository.saveAndFlush(
				new ProfileSkill(profile, skill, BigDecimal.ONE, null));

		mockMvc.perform(delete("/api/master/skills/" + skill.getId()).with(authentication(userPrincipal(manager))))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("SKILL_REFERENCED_BY_PROFILES"));

		assertTrue(skillRepository.existsById(skill.getId()));
		assertTrue(profileSkillRepository.existsByProfileIdAndSkillId(profile.getId(), skill.getId()));
		assertEquals(skill.getId(), profileSkillRepository.findById(profileSkill.getId()).orElseThrow().getSkill().getId());
	}

	@Test
	void memberCannotMutateSkillsInMigratedApplication() throws Exception {
		UserAccount member = saveUser(UserRole.MEMBER);
		mockMvc.perform(post("/api/master/skills").with(authentication(userPrincipal(member))).contentType(APPLICATION_JSON)
				.content("{\"name\":\"member-skill\",\"categoryId\":4}"))
				.andExpect(status().isForbidden());
	}

	private UserAccount saveUser() {
		return saveUser(UserRole.MEMBER);
	}

	private UserAccount saveUser(UserRole role) {
		return userRepository.saveAndFlush(new UserAccount(UUID.randomUUID() + "@example.com",
				"skill-user-" + UUID.randomUUID(), "hash", role, UserStatus.ACTIVE));
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

	private record CategorySeed(String code, String name) {
	}

	private record Constraint(String table, String name) {
	}

	private record MapRow(long id) {
	}
}
