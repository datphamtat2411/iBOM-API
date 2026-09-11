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
import com.fpt.ibom.profile.dto.CertificateRequest;
import com.fpt.ibom.profile.entity.Certificate;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.repository.CertificateRepository;
import com.fpt.ibom.profile.repository.ProfileRepository;
import com.fpt.ibom.profile.service.CertificateService;
import org.junit.jupiter.api.Test;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CertificateIntegrationTest.FixedClockConfiguration.class)
class CertificateIntegrationTest extends MySqlIntegrationTest {

	private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
	private static final Instant FIXED_INSTANT = Instant.parse("2026-09-10T18:30:00Z");
	private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 9, 11);

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private CertificateService certificateService;
	@Autowired
	private CertificateRepository certificateRepository;
	@Autowired
	private ProfileRepository profileRepository;
	@Autowired
	private UserAccountRepository userRepository;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void migrationCreatesCertificateSchemaAndRequiredConstraints() {
		assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'certificates'", Integer.class));
		assertEquals("varchar", jdbcTemplate.queryForObject("SELECT DATA_TYPE FROM information_schema.COLUMNS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'certificates' AND COLUMN_NAME = 'certificate_name'",
				String.class));
		assertEquals(255, jdbcTemplate.queryForObject("SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'certificates' AND COLUMN_NAME = 'certificate_name'",
				Integer.class));
		assertEquals("date", jdbcTemplate.queryForObject("SELECT DATA_TYPE FROM information_schema.COLUMNS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'certificates' AND COLUMN_NAME = 'issue_date'",
				String.class));
		assertEquals(1, constraintCount("uk_certificates_profile_name_issue_date"));
		assertEquals(1, constraintCount("fk_certificates_profile"));
		assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.STATISTICS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'certificates' "
				+ "AND INDEX_NAME = 'idx_certificates_profile_issue_date_id' "
				+ "AND SEQ_IN_INDEX = 3 AND COLUMN_NAME = 'id'", Integer.class));
		assertEquals("NO", nullable("certificate_name"));
		assertEquals("NO", nullable("issue_date"));
		assertEquals("NO", nullable("created_at"));
		assertEquals("NO", nullable("updated_at"));
		assertEquals(6, timestampPrecision("created_at"));
		assertEquals(6, timestampPrecision("updated_at"));
	}

	@Test
	void persistsCrudInvalidatesPreviewOrdersResultsAndPhysicallyDeletes() throws Exception {
		UserAccount user = saveUser();
		Profile profile = saveProfile(user, "Certificate CRUD Profile");
		ReflectionTestUtils.setField(profile, "hasPreviewed", true);
		profileRepository.saveAndFlush(profile);

		String createResponse = mockMvc.perform(post("/api/profiles/{id}/certificates", profile.getId())
				.with(authentication(userPrincipal(user))).contentType(MediaType.APPLICATION_JSON)
				.content(requestJson(" AWS ", "2024-01-01", 0)))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.data.profileVersion").value(1))
				.andExpect(jsonPath("$.data.certificate.certificateName").value("AWS"))
				.andReturn().getResponse().getContentAsString();
		Long certificateId = ((Number) com.jayway.jsonpath.JsonPath.read(createResponse, "$.data.certificate.id")).longValue();
		Certificate created = certificateRepository.findById(certificateId).orElseThrow();
		assertNotNull(created.getCreatedAt());
		assertNotNull(created.getUpdatedAt());
		assertFalse(profileRepository.findById(profile.getId()).orElseThrow().isHasPreviewed());

		certificateRepository.saveAndFlush(new Certificate(profile, "Older", LocalDate.of(2023, 1, 1)));
		jdbcTemplate.update("UPDATE profiles SET has_previewed = TRUE WHERE id = ?", profile.getId());
		mockMvc.perform(put("/api/profiles/{profileId}/certificates/{certificateId}", profile.getId(), certificateId)
				.with(authentication(userPrincipal(user))).contentType(MediaType.APPLICATION_JSON)
				.content(requestJson(" Updated AWS ", "2025-01-01", 1)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.profileVersion").value(2))
				.andExpect(jsonPath("$.data.certificate.certificateName").value("Updated AWS"));
		Certificate updated = certificateRepository.findById(certificateId).orElseThrow();
		assertEquals(created.getCreatedAt(), updated.getCreatedAt());
		assertTrue(!updated.getUpdatedAt().isBefore(created.getUpdatedAt()));
		assertFalse(profileRepository.findById(profile.getId()).orElseThrow().isHasPreviewed());

		mockMvc.perform(get("/api/profiles/{id}/certificates", profile.getId())
				.with(authentication(userPrincipal(user)))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].id").value(certificateId))
				.andExpect(jsonPath("$.data[0].certificateName").value("Updated AWS"))
				.andExpect(jsonPath("$.data[1].certificateName").value("Older"));

		mockMvc.perform(delete("/api/profiles/{profileId}/certificates/{certificateId}", profile.getId(), certificateId)
				.with(authentication(userPrincipal(user))).contentType(MediaType.APPLICATION_JSON)
				.content("{\"version\":2}" )).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.profileVersion").value(3));
		assertTrue(certificateRepository.findById(certificateId).isEmpty());
		assertEquals(3L, profileRepository.findById(profile.getId()).orElseThrow().getVersion());
		assertFalse(profileRepository.findById(profile.getId()).orElseThrow().isHasPreviewed());
	}

	@Test
	void enforcesDuplicateDatabaseConstraintAndServicePrecheck() {
		UserAccount user = saveUser();
		Profile profile = saveProfile(user, "Certificate Constraint Profile");
		insertCertificate(profile.getId(), "AWS", LocalDate.of(2020, 1, 1));

		assertDatabaseIntegrityViolation(() -> insertCertificate(profile.getId(), "AWS", LocalDate.of(2020, 1, 1)));
		ApiException duplicate = assertThrows(ApiException.class, () -> certificateService.create(user.getId(), profile.getId(),
				new CertificateRequest(" AWS ", LocalDate.of(2020, 1, 1), 0L)));
		assertEquals(ErrorCode.CERTIFICATE_ALREADY_EXISTS, duplicate.getErrorCode());
		assertDatabaseIntegrityViolation(() -> insertCertificate(Long.MAX_VALUE, "Other", LocalDate.of(2020, 1, 1)));
	}

	@Test
	void scopesOwnershipActiveProfilesAndVersions() {
		UserAccount owner = saveUser();
		UserAccount foreignUser = saveUser();
		Profile ownerProfile = saveProfile(owner, "Owner Certificate Profile");
		Profile foreignProfile = saveProfile(foreignUser, "Foreign Certificate Profile");
		Certificate foreignCertificate = certificateRepository.saveAndFlush(
				new Certificate(foreignProfile, "Foreign", LocalDate.of(2020, 1, 1)));

		ApiException foreign = assertThrows(ApiException.class,
				() -> certificateService.delete(owner.getId(), ownerProfile.getId(), foreignCertificate.getId(), 0L));
		assertEquals(ErrorCode.CERTIFICATE_NOT_FOUND, foreign.getErrorCode());

		certificateService.create(owner.getId(), ownerProfile.getId(),
				new CertificateRequest("Owned", LocalDate.of(2020, 1, 1), 0L));
		ApiException stale = assertThrows(ApiException.class, () -> certificateService.create(owner.getId(), ownerProfile.getId(),
				new CertificateRequest("Another", LocalDate.of(2021, 1, 1), 0L)));
		assertEquals(ErrorCode.PROFILE_VERSION_CONFLICT, stale.getErrorCode());

		Profile deleted = profileRepository.findById(ownerProfile.getId()).orElseThrow();
		deleted.softDelete(java.time.Instant.now());
		profileRepository.saveAndFlush(deleted);
		ApiException inactive = assertThrows(ApiException.class,
				() -> certificateService.list(owner.getId(), ownerProfile.getId()));
		assertEquals(ErrorCode.PROFILE_NOT_FOUND, inactive.getErrorCode());
	}

	@Test
	void rejectsFutureIssueDate() {
		UserAccount user = saveUser();
		Profile profile = saveProfile(user, "Future Certificate Profile");

		ApiException exception = assertThrows(ApiException.class, () -> certificateService.create(user.getId(), profile.getId(),
				new CertificateRequest("Future", BUSINESS_DATE.plusDays(1), 0L)));

		assertEquals(ErrorCode.CERTIFICATE_ISSUE_DATE_IN_FUTURE, exception.getErrorCode());
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
				+ "WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'certificates' AND CONSTRAINT_NAME = ?",
				Integer.class, constraintName);
	}

	private String nullable(String columnName) {
		return jdbcTemplate.queryForObject("SELECT IS_NULLABLE FROM information_schema.COLUMNS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'certificates' AND COLUMN_NAME = ?", String.class,
				columnName);
	}

	private Integer timestampPrecision(String columnName) {
		return jdbcTemplate.queryForObject("SELECT DATETIME_PRECISION FROM information_schema.COLUMNS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'certificates' AND COLUMN_NAME = ?", Integer.class,
				columnName);
	}

	private void insertCertificate(long profileId, String name, LocalDate issueDate) {
		jdbcTemplate.update("INSERT INTO certificates (profile_id, certificate_name, issue_date, created_at, updated_at) "
				+ "VALUES (?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))", profileId, name, issueDate);
	}

	private void assertDatabaseIntegrityViolation(Runnable action) {
		assertThrows(DataAccessException.class, action::run);
	}

	private UserAccount saveUser() {
		return userRepository.saveAndFlush(new UserAccount(UUID.randomUUID() + "@example.com",
				"certificate-member-" + UUID.randomUUID(), "hash", UserRole.MEMBER, UserStatus.ACTIVE));
	}

	private Profile saveProfile(UserAccount user, String name) {
		return profileRepository.saveAndFlush(new Profile(user, name, "First", "Last", "Engineer", BigDecimal.ONE,
				"Personality", "Summary"));
	}

	private Authentication userPrincipal(UserAccount user) {
		return new UsernamePasswordAuthenticationToken(
				new UserPrincipal(user.getId(), user.getEmail(), user.getUsername(), user.getRole()), null, List.of());
	}

	private String requestJson(String name, String issueDate, long version) {
		return "{\"certificateName\":\"" + name + "\",\"issueDate\":\"" + issueDate
				+ "\",\"version\":" + version + "}";
	}
}
