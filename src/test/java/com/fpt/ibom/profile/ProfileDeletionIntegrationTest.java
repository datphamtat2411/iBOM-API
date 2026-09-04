package com.fpt.ibom.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

@SpringBootTest
class ProfileDeletionIntegrationTest extends MySqlIntegrationTest {

	@Autowired
	private ProfileService profileService;
	@Autowired
	private ProfileRepository profileRepository;
	@Autowired
	private UserAccountRepository userAccountRepository;

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
		return new Profile(user, name, "Full Name", "Engineer", "user@example.com", "0123456789", "Address",
				new BigDecimal("3.5"));
	}
}
