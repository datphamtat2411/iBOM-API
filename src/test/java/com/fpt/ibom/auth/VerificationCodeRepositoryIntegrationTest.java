package com.fpt.ibom.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.time.Instant;

import com.fpt.ibom.auth.entity.VerificationCode;
import com.fpt.ibom.auth.entity.VerificationPurpose;
import com.fpt.ibom.auth.repository.VerificationCodeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class VerificationCodeRepositoryIntegrationTest {

	@Autowired
	private VerificationCodeRepository verificationCodeRepository;

	@Test
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
}
