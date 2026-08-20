package com.fpt.ibom.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.fpt.ibom.auth.entity.VerificationCode;
import com.fpt.ibom.auth.entity.VerificationPurpose;
import com.fpt.ibom.auth.repository.VerificationCodeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class VerificationCodeRepositoryIntegrationTest {

	@Autowired
	private VerificationCodeRepository verificationCodeRepository;
	@Autowired
	private PlatformTransactionManager transactionManager;

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
	void serializesConcurrentInvalidAttemptsAtTheFiveAttemptLimit() throws Exception {
		String email = "concurrent-reset-code-" + UUID.randomUUID() + "@example.com";
		Instant now = Instant.now();
		verificationCodeRepository.saveAndFlush(new VerificationCode(email, "a".repeat(64),
				VerificationPurpose.PASSWORD_RESET, now.plusSeconds(300), now));

		int submissions = 8;
		CountDownLatch ready = new CountDownLatch(submissions);
		CountDownLatch start = new CountDownLatch(1);
		TransactionTemplate transactions = new TransactionTemplate(transactionManager);
		ExecutorService executor = Executors.newFixedThreadPool(submissions);
		try {
			List<Future<Boolean>> results = new ArrayList<>();
			for (int submission = 0; submission < submissions; submission++) {
				results.add(executor.submit(() -> {
					ready.countDown();
					start.await();
					return transactions.execute(status -> verificationCodeRepository
							.findTopByEmailAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(email,
									VerificationPurpose.PASSWORD_RESET)
							.map(code -> {
								if (code.getFailedAttempts() >= 5) {
									return false;
								}
								code.incrementFailedAttempts();
								return true;
							}).orElse(false));
				}));
			}
			ready.await();
			start.countDown();

			int acceptedAttempts = 0;
			for (Future<Boolean> result : results) {
				if (result.get()) {
					acceptedAttempts++;
				}
			}

			VerificationCode code = verificationCodeRepository
					.findFirstByEmailAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(email, VerificationPurpose.PASSWORD_RESET)
					.orElseThrow();
			assertEquals(5, acceptedAttempts);
			assertEquals(5, code.getFailedAttempts());
		} finally {
			executor.shutdownNow();
		}
	}
}
