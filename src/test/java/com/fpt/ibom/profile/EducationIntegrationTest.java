package com.fpt.ibom.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.fpt.ibom.profile.entity.Education;
import com.fpt.ibom.profile.entity.EducationStatus;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.repository.EducationRepository;
import com.fpt.ibom.profile.repository.ProfileRepository;
import com.fpt.ibom.profile.service.EducationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class EducationIntegrationTest extends MySqlIntegrationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private EducationService educationService;
	@Autowired
	private EducationRepository educationRepository;
	@Autowired
	private ProfileRepository profileRepository;
	@Autowired
	private UserAccountRepository userRepository;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void persistsCanonicalEducationAndInvalidatesPreviewAcrossCrud() throws Exception {
		UserAccount user = saveUser();
		Profile profile = new Profile(user, "Education Profile", "First", "Last", "Engineer", BigDecimal.ONE,
				"Personality", "Summary");
		org.springframework.test.util.ReflectionTestUtils.setField(profile, "hasPreviewed", true);
		profile = profileRepository.saveAndFlush(profile);

		String createResponse = mockMvc.perform(post("/api/profiles/{id}/educations", profile.getId())
				.with(authentication(userPrincipal(user))).contentType(MediaType.APPLICATION_JSON)
				.content(requestJson(" School ", "ONGOING", "2020-01-01", "2025-01-01", 0)))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.data.profileVersion").value(1))
				.andExpect(jsonPath("$.data.education.schoolName").value("School"))
				.andReturn().getResponse().getContentAsString();
		Long educationId = ((Number) com.jayway.jsonpath.JsonPath.read(createResponse, "$.data.education.id")).longValue();

		Profile afterCreate = profileRepository.findById(profile.getId()).orElseThrow();
		Education created = educationRepository.findById(educationId).orElseThrow();
		assertEquals(1L, afterCreate.getVersion());
		assertFalse(afterCreate.isHasPreviewed());
		assertNull(created.getEndDate());
		assertEquals(EducationStatus.ONGOING, created.getStatus());
		assertNotNull(created.getCreatedAt());
		assertNotNull(created.getUpdatedAt());

		mockMvc.perform(put("/api/profiles/{profileId}/educations/{educationId}", profile.getId(), educationId)
				.with(authentication(userPrincipal(user))).contentType(MediaType.APPLICATION_JSON)
				.content(requestJson("Updated School", "COMPLETED", "2020-01-01", "2022-01-01", 1)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.profileVersion").value(2));
		Education updated = educationRepository.findById(educationId).orElseThrow();
		assertEquals(EducationStatus.COMPLETED, updated.getStatus());
		assertEquals(created.getCreatedAt(), updated.getCreatedAt());
		assertNotNull(updated.getUpdatedAt());
		assertFalse(updated.getUpdatedAt().isBefore(created.getUpdatedAt()));

		mockMvc.perform(get("/api/profiles/{id}/educations", profile.getId()).with(authentication(userPrincipal(user))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(educationId))
				.andExpect(jsonPath("$.data[0].schoolName").value("Updated School"));

		mockMvc.perform(delete("/api/profiles/{profileId}/educations/{educationId}", profile.getId(), educationId)
				.with(authentication(userPrincipal(user))).contentType(MediaType.APPLICATION_JSON)
				.content("{\"version\":2}" )).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.profileVersion").value(3));
		assertTrue(educationRepository.findById(educationId).isEmpty());
		assertEquals(3L, profileRepository.findById(profile.getId()).orElseThrow().getVersion());
	}

	@Test
	void scopesForeignAndDeletedProfilesAndRejectsStaleMutations() {
		UserAccount owner = saveUser();
		UserAccount foreignUser = saveUser();
		Profile profile = saveProfile(owner, "Owner Profile");
		Profile foreignProfile = saveProfile(foreignUser, "Foreign Profile");
		Education foreignEducation = educationRepository.saveAndFlush(new Education(foreignProfile, "Foreign School", "Degree",
				null, java.time.LocalDate.of(2020, 1, 1), null, EducationStatus.ONGOING));

		ApiException foreign = assertThrows(ApiException.class,
				() -> educationService.delete(owner.getId(), profile.getId(), foreignEducation.getId(), 0L));
		assertEquals(ErrorCode.EDUCATION_NOT_FOUND, foreign.getErrorCode());

		ApiException stale = assertThrows(ApiException.class,
				() -> educationService.create(owner.getId(), profile.getId(),
						new com.fpt.ibom.profile.dto.EducationRequest("School", "Degree", null,
								java.time.LocalDate.of(2020, 1, 1), null, "ONGOING", 1L)));
		assertEquals(ErrorCode.PROFILE_VERSION_CONFLICT, stale.getErrorCode());

		Profile otherActive = saveProfile(owner, "Other Active");
		educationService.delete(owner.getId(), otherActive.getId(),
				educationRepository.saveAndFlush(new Education(otherActive, "School", "Degree", null,
					java.time.LocalDate.of(2020, 1, 1), null, EducationStatus.ONGOING)).getId(), 0L);
		Profile deletedProfile = profileRepository.findById(profile.getId()).orElseThrow();
		deletedProfile.softDelete(java.time.Instant.now());
		profileRepository.saveAndFlush(deletedProfile);
		ApiException deleted = assertThrows(ApiException.class, () -> educationService.list(owner.getId(), profile.getId()));
		assertEquals(ErrorCode.PROFILE_NOT_FOUND, deleted.getErrorCode());
	}

	@Test
	void migrationCreatesOwnedEducationSchema() {
		assertTrue(jdbcTemplate.queryForList("SELECT TABLE_NAME FROM information_schema.TABLES "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'educations'", String.class).contains("educations"));
		assertEquals("YES", jdbcTemplate.queryForObject("SELECT IS_NULLABLE FROM information_schema.COLUMNS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'educations' AND COLUMN_NAME = 'end_date'", String.class));
		assertEquals("varchar", jdbcTemplate.queryForObject("SELECT DATA_TYPE FROM information_schema.COLUMNS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'educations' AND COLUMN_NAME = 'status'", String.class));
		assertEquals("NO", jdbcTemplate.queryForObject("SELECT IS_NULLABLE FROM information_schema.COLUMNS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'educations' AND COLUMN_NAME = 'created_at'", String.class));
		assertEquals("NO", jdbcTemplate.queryForObject("SELECT IS_NULLABLE FROM information_schema.COLUMNS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'educations' AND COLUMN_NAME = 'updated_at'", String.class));
		assertEquals(6, jdbcTemplate.queryForObject("SELECT DATETIME_PRECISION FROM information_schema.COLUMNS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'educations' AND COLUMN_NAME = 'created_at'", Integer.class));
		assertEquals(6, jdbcTemplate.queryForObject("SELECT DATETIME_PRECISION FROM information_schema.COLUMNS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'educations' AND COLUMN_NAME = 'updated_at'", Integer.class));
	}

	private UserAccount saveUser() {
		return userRepository.saveAndFlush(new UserAccount(UUID.randomUUID() + "@example.com", "member" + UUID.randomUUID(),
				"hash", UserRole.MEMBER, UserStatus.ACTIVE));
	}

	private Profile saveProfile(UserAccount user, String name) {
		return profileRepository.saveAndFlush(new Profile(user, name, "First", "Last", "Engineer", BigDecimal.ONE,
				"Personality", "Summary"));
	}

	private Authentication userPrincipal(UserAccount user) {
		return new UsernamePasswordAuthenticationToken(
				new UserPrincipal(user.getId(), user.getEmail(), user.getUsername(), user.getRole()), null, List.of());
	}

	private String requestJson(String schoolName, String status, String startDate, String endDate, long version) {
		return "{\"schoolName\":\"" + schoolName + "\",\"degree\":\"Degree\",\"fieldOfStudy\":\"Field\","
				+ "\"startDate\":\"" + startDate + "\",\"endDate\":"
				+ (endDate == null ? "null" : "\"" + endDate + "\"") + ",\"status\":\"" + status
				+ "\",\"version\":" + version + "}";
	}
}
