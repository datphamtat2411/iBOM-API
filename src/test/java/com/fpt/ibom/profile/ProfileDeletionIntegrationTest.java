package com.fpt.ibom.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.fpt.ibom.MySqlIntegrationTest;
import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.repository.ProfileRepository;
import com.fpt.ibom.profile.service.ProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class ProfileDeletionIntegrationTest extends MySqlIntegrationTest {

	@Autowired
	private ProfileService profileService;
	@Autowired
	private ProfileRepository profileRepository;
	@Autowired
	private UserAccountRepository userAccountRepository;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void persistsCanonicalFieldsAtTheirDatabaseBoundaries() {
		UserAccount user = userAccountRepository.saveAndFlush(user("profile-boundary@example.com"));
		Profile profile = new Profile(user, "Boundary", "f".repeat(100), "l".repeat(100), "j".repeat(100),
				new BigDecimal("0"), "p".repeat(4000), "s".repeat(4000));

		Profile persisted = profileRepository.saveAndFlush(profile);

		assertEquals(100, jdbcTemplate.queryForObject(
				"SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS "
						+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'profiles' AND COLUMN_NAME = 'first_name'",
				Integer.class));
		assertEquals(4000, jdbcTemplate.queryForObject(
				"SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS "
						+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'profiles' AND COLUMN_NAME = 'personality'",
				Integer.class));
		assertEquals(4000, persisted.getTechnicalSummary().length());
	}

	@Test
	void schemaRemovesLegacyProfileColumnsAndRequiresCanonicalFields() {
		List<String> columns = jdbcTemplate.queryForList(
				"SELECT COLUMN_NAME FROM information_schema.COLUMNS "
						+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'profiles'",
				String.class);

		assertTrue(columns.containsAll(List.of("profile_name", "first_name", "last_name", "job_title",
				"years_of_experience", "personality", "technical_summary")));
		assertTrue(List.of("full_name", "email", "phone_number", "address").stream().noneMatch(columns::contains));
		assertEquals("NO", jdbcTemplate.queryForObject(
				"SELECT IS_NULLABLE FROM information_schema.COLUMNS "
						+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'profiles' AND COLUMN_NAME = 'personality'",
				String.class));
	}

	@Test
	void databaseRejectsNullCanonicalAboutMeField() {
		UserAccount user = userAccountRepository.saveAndFlush(user("profile-null@example.com"));
		Profile profile = new Profile(user, "Null", "First", "Last", "Engineer", new BigDecimal("1"), null,
				"Summary");

		assertThrows(DataIntegrityViolationException.class, () -> profileRepository.saveAndFlush(profile));
	}

	@Test
	void softDeletedProfileRemainsPersistedAndIsExcludedFromActiveReads() {
		UserAccount user = userAccountRepository.saveAndFlush(user("delete-read@example.com"));
		Profile deleted = profileRepository.saveAndFlush(profile(user, "Deleted"));
		profileRepository.saveAndFlush(profile(user, "Active"));

		profileService.delete(user.getId(), deleted.getId());

		assertNotNull(profileRepository.findById(deleted.getId()).orElseThrow().getDeletedAt());
		assertEquals(List.of("Active"), profileService.list(user.getId()).stream()
				.map(summary -> summary.profileName()).toList());
		assertThrows(ApiException.class, () -> profileService.get(user.getId(), deleted.getId()));
	}

	@Test
	void serializesConcurrentDeletionsAndPreservesOneActiveProfile() throws Exception {
		UserAccount user = userAccountRepository.saveAndFlush(user("delete-concurrent@example.com"));
		Profile first = profileRepository.saveAndFlush(profile(user, "First"));
		Profile second = profileRepository.saveAndFlush(profile(user, "Second"));
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<ErrorCode> firstResult = submitDeletion(executor, ready, start, user.getId(), first.getId());
			Future<ErrorCode> secondResult = submitDeletion(executor, ready, start, user.getId(), second.getId());
			ready.await();
			start.countDown();

			List<ErrorCode> results = Arrays.asList(firstResult.get(), secondResult.get());
			assertEquals(1, results.stream().filter(code -> code == null).count());
			assertEquals(1, results.stream().filter(ErrorCode.PROFILE_LAST_ACTIVE_CANNOT_DELETE::equals).count());
			assertEquals(1, profileRepository.countByUserIdAndDeletedAtIsNull(user.getId()));
		} finally {
			executor.shutdownNow();
		}
	}

	private Future<ErrorCode> submitDeletion(ExecutorService executor, CountDownLatch ready, CountDownLatch start,
			Long userId, Long profileId) {
		return executor.submit(() -> {
			ready.countDown();
			start.await();
			try {
				profileService.delete(userId, profileId);
				return null;
			} catch (ApiException exception) {
				return exception.getErrorCode();
			}
		});
	}

	private UserAccount user(String email) {
		return new UserAccount(email, email.substring(0, email.indexOf('@')), "hash", UserRole.MEMBER,
				UserStatus.ACTIVE);
	}

	private Profile profile(UserAccount user, String name) {
		return new Profile(user, name, "First", "Last", "Engineer", new BigDecimal("3.5"), "Personality",
				"Summary");
	}
}
