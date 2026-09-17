package com.fpt.ibom.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import java.time.LocalDate;
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
import com.fpt.ibom.profile.dto.ProjectMutationResponse;
import com.fpt.ibom.profile.dto.ProjectRequest;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.entity.Project;
import com.fpt.ibom.profile.entity.ProjectStatus;
import com.fpt.ibom.profile.repository.ProfileRepository;
import com.fpt.ibom.profile.repository.ProjectRepository;
import com.fpt.ibom.profile.service.ProjectService;
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
class ProjectIntegrationTest extends MySqlIntegrationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ProjectService projectService;
	@Autowired
	private ProjectRepository projectRepository;
	@Autowired
	private ProfileRepository profileRepository;
	@Autowired
	private UserAccountRepository userRepository;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void migrationCreatesProjectSchemaConstraintsAndOrderingIndex() {
		assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'projects'", Integer.class));
		assertEquals("varchar", dataType("name"));
		assertEquals(255, jdbcTemplate.queryForObject("SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'projects' AND COLUMN_NAME = 'name'", Integer.class));
		assertEquals("text", dataType("description"));
		assertEquals("date", dataType("start_date"));
		assertEquals("date", dataType("end_date"));
		assertEquals("varchar", dataType("status"));
		assertEquals("int", dataType("team_size"));
		assertEquals("text", dataType("responsibilities"));
		assertEquals("text", dataType("programming_languages"));
		assertEquals("text", dataType("tools"));
		assertEquals("NO", nullable("name"));
		assertEquals("NO", nullable("description"));
		assertEquals("YES", nullable("start_date"));
		assertEquals("YES", nullable("end_date"));
		assertEquals("NO", nullable("status"));
		assertEquals("NO", nullable("position"));
		assertEquals("YES", nullable("team_size"));
		assertEquals("YES", nullable("responsibilities"));
		assertEquals("YES", nullable("programming_languages"));
		assertEquals("YES", nullable("tools"));
		assertEquals("NO", nullable("created_at"));
		assertEquals("NO", nullable("updated_at"));
		assertEquals(1, constraintCount("fk_projects_profile"));
		assertEquals(1, constraintCount("chk_projects_status"));
		assertEquals(1, constraintCount("chk_projects_team_size"));
		assertEquals(1, constraintCount("chk_projects_dates"));
		String indexName = "idx_projects_profile_status_end_date_start_date_created_at";
		assertEquals(1, indexColumnCount(indexName, 1, "profile_id"));
		assertEquals(1, indexColumnCount(indexName, 2, "status"));
		assertEquals(1, indexColumnCount(indexName, 3, "end_date"));
		assertEquals(1, indexColumnCount(indexName, 4, "start_date"));
		assertEquals(1, indexColumnCount(indexName, 5, "created_at"));
	}

	@Test
	void persistsCrudCanonicalizesTextInvalidatesPreviewAndPhysicallyDeletes() throws Exception {
		UserAccount user = saveUser();
		Profile profile = saveProfile(user, "Project CRUD Profile");
		ReflectionTestUtils.setField(profile, "hasPreviewed", true);
		profileRepository.saveAndFlush(profile);

		String createResponse = mockMvc.perform(post("/api/profiles/{id}/projects", profile.getId())
				.with(authentication(userPrincipal(user))).contentType(MediaType.APPLICATION_JSON)
				.content(requestJson(" Project ", "ONGOING", "2020-01-01", "2025-01-01", 3,
						"  Led team\nShipped product  ", "   ", "   ", 0)))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.data.profileVersion").value(1))
				.andExpect(jsonPath("$.data.project.name").value("Project"))
				.andExpect(jsonPath("$.data.project.endDate").value(org.hamcrest.Matchers.nullValue()))
				.andReturn().getResponse().getContentAsString();
		Long projectId = ((Number) com.jayway.jsonpath.JsonPath.read(createResponse, "$.data.project.id")).longValue();

		Project created = projectRepository.findById(projectId).orElseThrow();
		assertEquals("Led team\nShipped product", created.getResponsibilities());
		assertNull(created.getProgrammingLanguages());
		assertNull(created.getTools());
		assertEquals(ProjectStatus.ONGOING, created.getStatus());
		assertNull(created.getEndDate());
		assertNotNull(created.getCreatedAt());
		assertNotNull(created.getUpdatedAt());
		assertFalse(profileRepository.findById(profile.getId()).orElseThrow().isHasPreviewed());

		mockMvc.perform(put("/api/profiles/{profileId}/projects/{projectId}", profile.getId(), projectId)
				.with(authentication(userPrincipal(user))).contentType(MediaType.APPLICATION_JSON)
				.content(requestJson("Updated Project", "COMPLETED", "2020-01-01", "2022-01-01", 4,
						"Responsibilities", "Java", "Docker", 1)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.profileVersion").value(2));
		Project completed = projectRepository.findById(projectId).orElseThrow();
		assertEquals(ProjectStatus.COMPLETED, completed.getStatus());
		assertEquals(LocalDate.of(2022, 1, 1), completed.getEndDate());
		assertEquals(created.getCreatedAt(), completed.getCreatedAt());
		assertTrue(!completed.getUpdatedAt().isBefore(created.getUpdatedAt()));

		mockMvc.perform(put("/api/profiles/{profileId}/projects/{projectId}", profile.getId(), projectId)
				.with(authentication(userPrincipal(user))).contentType(MediaType.APPLICATION_JSON)
				.content(requestJson("Ongoing Again", "ONGOING", "2020-01-01", "2030-01-01", 4,
						"Responsibilities", "Java", "Docker", 2)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.profileVersion").value(3));
		Project ongoingAgain = projectRepository.findById(projectId).orElseThrow();
		assertEquals(ProjectStatus.ONGOING, ongoingAgain.getStatus());
		assertNull(ongoingAgain.getEndDate());

		mockMvc.perform(delete("/api/profiles/{profileId}/projects/{projectId}", profile.getId(), projectId)
				.with(authentication(userPrincipal(user))).contentType(MediaType.APPLICATION_JSON)
				.content("{\"version\":3}" )).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.profileVersion").value(4));
		assertTrue(projectRepository.findById(projectId).isEmpty());
		assertEquals(4L, profileRepository.findById(profile.getId()).orElseThrow().getVersion());
		assertFalse(profileRepository.findById(profile.getId()).orElseThrow().isHasPreviewed());
	}

	@Test
	void persistsNullableOptionalFieldsAndAllowsCompletedProjectWithoutStartDate() {
		UserAccount user = saveUser();
		Profile profile = saveProfile(user, "Project Nullable Profile");
		ReflectionTestUtils.setField(profile, "hasPreviewed", true);
		profileRepository.saveAndFlush(profile);

		ProjectMutationResponse response = projectService.create(userPrincipalValue(user), profile.getId(), new ProjectRequest(
				"Completed Project", "Description", null, LocalDate.of(2024, 1, 1), "COMPLETED", "Engineer", null,
				null, null, null, profile.getVersion()));
		Project completed = projectRepository.findById(response.project().id()).orElseThrow();
		assertNull(completed.getStartDate());
		assertEquals(LocalDate.of(2024, 1, 1), completed.getEndDate());
		assertNull(completed.getTeamSize());
		assertNull(completed.getResponsibilities());
		assertNull(completed.getProgrammingLanguages());
		assertNull(completed.getTools());
	}

	@Test
	void listsOngoingFirstThenDatesAndCreationOrder() {
		UserAccount user = saveUser();
		Profile profile = saveProfile(user, "Project Ordering Profile");
		projectRepository.saveAndFlush(project(profile, "Ongoing Older", ProjectStatus.ONGOING, LocalDate.of(2020, 1, 1), null));
		projectRepository.saveAndFlush(project(profile, "Ongoing Newer", ProjectStatus.ONGOING, LocalDate.of(2022, 1, 1), null));
		projectRepository.saveAndFlush(project(profile, "Completed Late", ProjectStatus.COMPLETED, LocalDate.of(2023, 1, 1),
				LocalDate.of(2024, 1, 1)));
		projectRepository.saveAndFlush(project(profile, "Completed Same End Older", ProjectStatus.COMPLETED,
				LocalDate.of(2020, 1, 1), LocalDate.of(2024, 1, 1)));
		projectRepository.saveAndFlush(project(profile, "Completed Same End Newer", ProjectStatus.COMPLETED,
				LocalDate.of(2021, 1, 1), LocalDate.of(2024, 1, 1)));
		projectRepository.saveAndFlush(project(profile, "Completed Early", ProjectStatus.COMPLETED, LocalDate.of(2021, 1, 1),
				LocalDate.of(2022, 1, 1)));

		List<String> names = projectService.list(userPrincipalValue(user), profile.getId()).stream().map(response -> response.name()).toList();

		assertEquals(List.of("Ongoing Newer", "Ongoing Older", "Completed Late", "Completed Same End Newer",
				"Completed Same End Older", "Completed Early"), names);
	}

	@Test
	void scopesOwnershipDeletedProfilesAndProfileVersions() {
		UserAccount owner = saveUser();
		UserAccount foreignUser = saveUser();
		Profile ownerProfile = saveProfile(owner, "Owner Project Profile");
		Profile foreignProfile = saveProfile(foreignUser, "Foreign Project Profile");
		Project foreignProject = projectRepository.saveAndFlush(
				project(foreignProfile, "Foreign", ProjectStatus.ONGOING, LocalDate.of(2020, 1, 1), null));

		ApiException foreign = assertThrows(ApiException.class,
				() -> projectService.delete(userPrincipalValue(owner), ownerProfile.getId(), foreignProject.getId(), 0L));
		assertEquals(ErrorCode.PROJECT_NOT_FOUND, foreign.getErrorCode());

		projectService.create(userPrincipalValue(owner), ownerProfile.getId(), request("ONGOING", LocalDate.of(2020, 1, 1), null, 0L));
		ApiException stale = assertThrows(ApiException.class, () -> projectService.create(userPrincipalValue(owner), ownerProfile.getId(),
				request("ONGOING", LocalDate.of(2021, 1, 1), null, 0L)));
		assertEquals(ErrorCode.PROFILE_VERSION_CONFLICT, stale.getErrorCode());

		Profile deleted = profileRepository.findById(ownerProfile.getId()).orElseThrow();
		deleted.softDelete(java.time.Instant.now());
		profileRepository.saveAndFlush(deleted);
		ApiException inactive = assertThrows(ApiException.class,
				() -> projectService.list(userPrincipalValue(owner), ownerProfile.getId()));
		assertEquals(ErrorCode.PROFILE_NOT_FOUND, inactive.getErrorCode());
	}

	@Test
	void enforcesProjectDatabaseConstraints() {
		UserAccount user = saveUser();
		Profile profile = saveProfile(user, "Project Constraint Profile");

		assertDatabaseIntegrityViolation(() -> insertProject(profile.getId(), "INVALID", 1, LocalDate.of(2020, 1, 1), null));
		assertDatabaseIntegrityViolation(() -> insertProject(profile.getId(), "ONGOING", 0, LocalDate.of(2020, 1, 1), null));
		assertDatabaseIntegrityViolation(() -> insertProject(profile.getId(), "ONGOING", 1, LocalDate.of(2020, 1, 1),
				LocalDate.of(2021, 1, 1)));
		assertDatabaseIntegrityViolation(() -> insertProject(profile.getId(), "COMPLETED", 1, LocalDate.of(2020, 1, 1), null));
		assertDatabaseIntegrityViolation(() -> insertProject(profile.getId(), "COMPLETED", 1, LocalDate.of(2021, 1, 1),
				LocalDate.of(2020, 1, 1)));
		assertDoesNotThrow(() -> insertProject(profile.getId(), "COMPLETED", 1, null, LocalDate.of(2020, 1, 1),
				"Responsibilities"));
		assertDoesNotThrow(() -> insertProject(profile.getId(), "ONGOING", null, null, null, null));
		assertDatabaseIntegrityViolation(() -> insertProject(Long.MAX_VALUE, "ONGOING", 1, LocalDate.of(2020, 1, 1), null));
	}

	private String dataType(String columnName) {
		return jdbcTemplate.queryForObject("SELECT DATA_TYPE FROM information_schema.COLUMNS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'projects' AND COLUMN_NAME = ?", String.class, columnName);
	}

	private String nullable(String columnName) {
		return jdbcTemplate.queryForObject("SELECT IS_NULLABLE FROM information_schema.COLUMNS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'projects' AND COLUMN_NAME = ?", String.class, columnName);
	}

	private int constraintCount(String constraintName) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS "
				+ "WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'projects' AND CONSTRAINT_NAME = ?",
				Integer.class, constraintName);
	}

	private int indexColumnCount(String indexName, int sequence, String columnName) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.STATISTICS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'projects' AND INDEX_NAME = ? "
				+ "AND SEQ_IN_INDEX = ? AND COLUMN_NAME = ?", Integer.class, indexName, sequence, columnName);
	}

	private void insertProject(long profileId, String status, int teamSize, LocalDate startDate, LocalDate endDate) {
		insertProject(profileId, status, Integer.valueOf(teamSize), startDate, endDate, "Responsibilities");
	}

	private void insertProject(long profileId, String status, Integer teamSize, LocalDate startDate, LocalDate endDate,
			String responsibilities) {
		jdbcTemplate.update("INSERT INTO projects (profile_id, name, description, start_date, end_date, status, position, "
				+ "team_size, responsibilities, programming_languages, tools, created_at, updated_at) "
				+ "VALUES (?, 'Project', 'Description', ?, ?, ?, 'Engineer', ?, ?, NULL, NULL, "
				+ "CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))", profileId, startDate, endDate, status, teamSize,
				responsibilities);
	}

	private void assertDatabaseIntegrityViolation(Runnable action) {
		assertThrows(DataAccessException.class, action::run);
	}

	private UserAccount saveUser() {
		return userRepository.saveAndFlush(new UserAccount(UUID.randomUUID() + "@example.com",
				"project-member-" + UUID.randomUUID(), "hash", UserRole.MEMBER, UserStatus.ACTIVE));
	}

	private Profile saveProfile(UserAccount user, String name) {
		return profileRepository.saveAndFlush(new Profile(user, name, "First", "Last", "Engineer", BigDecimal.ONE,
				"Personality", "Summary"));
	}

	private Project project(Profile profile, String name, ProjectStatus status, LocalDate startDate, LocalDate endDate) {
		return new Project(profile, name, "Description", startDate, endDate, status, "Engineer", 1,
				"Responsibilities", null, null);
	}

	private Authentication userPrincipal(UserAccount user) {
		return new UsernamePasswordAuthenticationToken(userPrincipalValue(user), null, List.of());
	}

	private UserPrincipal userPrincipalValue(UserAccount user) {
		return new UserPrincipal(user.getId(), user.getEmail(), user.getUsername(), user.getRole());
	}

	private ProjectRequest request(String status, LocalDate startDate, LocalDate endDate, long version) {
		return new ProjectRequest("Project", "Description", startDate, endDate, status, "Engineer", 1,
				"Responsibilities", "Java", "Docker", version);
	}

	private String requestJson(String name, String status, String startDate, String endDate, int teamSize,
			String responsibilities, String programmingLanguages, String tools, long version) {
		return "{\"name\":\"" + name + "\",\"description\":\"Description\",\"startDate\":\"" + startDate
				+ "\",\"endDate\":" + (endDate == null ? "null" : "\"" + endDate + "\"") + ",\"status\":\""
				+ status + "\",\"position\":\"Engineer\",\"teamSize\":" + teamSize + ",\"responsibilities\":\""
				+ responsibilities.replace("\n", "\\n") + "\",\"programmingLanguages\":\"" + programmingLanguages
				+ "\",\"tools\":\"" + tools + "\",\"version\":" + version + "}";
	}
}
