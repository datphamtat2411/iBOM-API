package com.fpt.ibom.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.fpt.ibom.auth.dto.RegistrationRequest;
import com.fpt.ibom.auth.entity.VerificationCode;
import com.fpt.ibom.auth.entity.VerificationPurpose;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.repository.VerificationCodeRepository;
import com.fpt.ibom.auth.service.RegistrationService;
import com.fpt.ibom.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.auth.registration.allowed-domains=gmail.com")
class VerificationCodeRepositoryIntegrationTest {

	@Autowired
	private VerificationCodeRepository verificationCodeRepository;
	@Autowired
	private UserAccountRepository userAccountRepository;
	@Autowired
	private RegistrationService registrationService;

	@Test
	@Transactional
	void findsAndLocksNewestUnusedPasswordResetCode() {
		String email = "latest-reset-code@example.com";
		Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
		verificationCodeRepository.saveAndFlush(new VerificationCode(email, "a".repeat(64),
				VerificationPurpose.PASSWORD_RESET, createdAt.plusSeconds(300), createdAt));
		verificationCodeRepository.saveAndFlush(new VerificationCode(email, "b".repeat(64),
				VerificationPurpose.PASSWORD_RESET, createdAt.plusSeconds(600), createdAt.plusSeconds(60)));

		VerificationCode code = assertDoesNotThrow(() -> verificationCodeRepository
				.findTopByEmailAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(email, VerificationPurpose.PASSWORD_RESET)
				.orElseThrow());

		assertEquals("b".repeat(64), code.getCodeHash());
	}

	@Test
	void persistsFailedRegistrationAttemptDespiteApiException() {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String email = "failed-registration-" + suffix + "@gmail.com";
		Instant now = Instant.now();
		verificationCodeRepository.saveAndFlush(new VerificationCode(email, sha256("123456"),
				VerificationPurpose.REGISTRATION, now.plusSeconds(300), now));

		ApiException exception = assertThrows(ApiException.class, () -> registrationService.register(
				new RegistrationRequest(email, "member" + suffix, "Password1!", "654321")));

		assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
		VerificationCode code = verificationCodeRepository
				.findFirstByEmailAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(email, VerificationPurpose.REGISTRATION)
				.orElseThrow();
		assertEquals(1, code.getFailedAttempts());
	}

	@Test
	void serializesConcurrentInvalidRegistrationAttemptsAtTheFiveAttemptLimit() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String email = "concurrent-registration-" + suffix + "@gmail.com";
		String username = "member" + suffix;
		Instant now = Instant.now();
		verificationCodeRepository.saveAndFlush(new VerificationCode(email, sha256("123456"),
				VerificationPurpose.REGISTRATION, now.plusSeconds(300), now));

		int submissions = 8;
		CountDownLatch ready = new CountDownLatch(submissions);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(submissions);
		try {
			List<Future<Boolean>> results = new ArrayList<>();
			for (int submission = 0; submission < submissions; submission++) {
				results.add(executor.submit(() -> {
					ready.countDown();
					start.await();
					try {
						registrationService.register(new RegistrationRequest(email, username, "Password1!", "654321"));
						return false;
					} catch (ApiException exception) {
						return exception.getStatus() == HttpStatus.BAD_REQUEST;
					}
				}));
			}
			ready.await();
			start.countDown();

			int invalidResponses = 0;
			for (Future<Boolean> result : results) {
				if (result.get()) {
					invalidResponses++;
				}
			}

			VerificationCode code = verificationCodeRepository
					.findFirstByEmailAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(email, VerificationPurpose.REGISTRATION)
					.orElseThrow();
			assertEquals(submissions, invalidResponses);
			assertEquals(5, code.getFailedAttempts());

			ApiException exhausted = assertThrows(ApiException.class, () -> registrationService.register(
					new RegistrationRequest(email, username, "Password1!", "123456")));
			assertEquals(HttpStatus.BAD_REQUEST, exhausted.getStatus());
			assertFalse(userAccountRepository.existsByEmailIgnoreCase(email));
		} finally {
			executor.shutdownNow();
		}
	}

	private String sha256(String value) {
		try {
			return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (java.security.NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
