package com.fpt.ibom.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Optional;

import com.fpt.ibom.auth.dto.PasswordResetCodeRequest;
import com.fpt.ibom.auth.dto.PasswordResetCodeVerificationRequest;
import com.fpt.ibom.auth.dto.PasswordResetRequest;
import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.entity.VerificationCode;
import com.fpt.ibom.auth.entity.VerificationPurpose;
import com.fpt.ibom.auth.repository.RefreshTokenRepository;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.repository.VerificationCodeRepository;
import com.fpt.ibom.auth.service.PasswordResetService;
import com.fpt.ibom.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.fpt.ibom.auth.service.MailService;

class PasswordResetServiceTest {

	private final UserAccountRepository users = mock(UserAccountRepository.class);
	private final VerificationCodeRepository codes = mock(VerificationCodeRepository.class);
	private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
	private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
	private final MailService mailService = mock(MailService.class);
	private final PasswordResetService passwordResetService = new PasswordResetService(users, codes, refreshTokens,
			passwordEncoder, mailService);

	@Test
	void requestsCodeForNormalizedExistingEmailAndSubmitsEligibleMailDelivery() {
		when(codes.countByEmailAndPurposeAndCreatedAtAfter(eq("user@example.com"), eq(VerificationPurpose.PASSWORD_RESET), any()))
				.thenReturn(0L);
		when(users.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user(UserStatus.ACTIVE)));

		passwordResetService.requestCode(new PasswordResetCodeRequest(" User@EXAMPLE.COM "));

		ArgumentCaptor<VerificationCode> code = ArgumentCaptor.forClass(VerificationCode.class);
		verify(codes).save(code.capture());
		assertEquals(64, code.getValue().getCodeHash().length());
		assertNotNull(code.getValue().getExpiresAt());
		assertEquals(0, code.getValue().getFailedAttempts());
		verify(mailService).sendPasswordResetCode(eq("user@example.com"), any(), eq(true));
	}

	@Test
	void treatsUnknownEmailLikeExistingEmailAndSubmitsIneligibleMailDelivery() {
		when(codes.countByEmailAndPurposeAndCreatedAtAfter(eq("unknown@example.com"), eq(VerificationPurpose.PASSWORD_RESET), any()))
				.thenReturn(0L);
		when(users.findByEmailIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());

		passwordResetService.requestCode(new PasswordResetCodeRequest("unknown@example.com"));

		verify(codes).save(any(VerificationCode.class));
		verify(mailService).sendPasswordResetCode(eq("unknown@example.com"), any(), eq(false));
	}

	@Test
	void limitsRequestsForBothExistingAndUnknownEmails() {
		when(codes.countByEmailAndPurposeAndCreatedAtAfter(any(), eq(VerificationPurpose.PASSWORD_RESET), any())).thenReturn(5L);

		ApiException existing = assertThrows(ApiException.class,
				() -> passwordResetService.requestCode(new PasswordResetCodeRequest("user@example.com")));
		ApiException unknown = assertThrows(ApiException.class,
				() -> passwordResetService.requestCode(new PasswordResetCodeRequest("unknown@example.com")));

		assertEquals(HttpStatus.TOO_MANY_REQUESTS, existing.getStatus());
		assertEquals(existing.getMessage(), unknown.getMessage());
	}

	@Test
	void verifiesOnlyLatestUnexpiredMatchingCodeWithoutConsumingIt() {
		VerificationCode code = code("123456", Instant.now().plusSeconds(300));
		when(users.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user(UserStatus.ACTIVE)));
		when(codes.findTopByEmailAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc("user@example.com", VerificationPurpose.PASSWORD_RESET))
				.thenReturn(Optional.of(code));

		passwordResetService.verifyCode(new PasswordResetCodeVerificationRequest("user@example.com", "123456"));

		assertNull(code.getUsedAt());
		assertEquals(0, code.getFailedAttempts());
	}

	@Test
	void rejectsExpiredUsedIncorrectAndUnknownAccountCodes() {
		when(users.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user(UserStatus.ACTIVE)));
		when(codes.findTopByEmailAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc("user@example.com", VerificationPurpose.PASSWORD_RESET))
				.thenReturn(Optional.of(code("123456", Instant.now().minusSeconds(1))));
		assertInvalidVerification("user@example.com", "123456");

		when(codes.findTopByEmailAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc("user@example.com", VerificationPurpose.PASSWORD_RESET))
				.thenReturn(Optional.of(code("654321", Instant.now().plusSeconds(300))));
		assertInvalidVerification("user@example.com", "123456");

		when(users.findByEmailIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());
		assertInvalidVerification("unknown@example.com", "123456");
	}

	@Test
	void incrementsFailedAttemptsForIncorrectStandaloneVerification() {
		VerificationCode code = code("123456", Instant.now().plusSeconds(300));
		stubStandaloneCode(code);

		assertInvalidVerification("user@example.com", "654321");

		assertEquals(1, code.getFailedAttempts());
		assertNull(code.getUsedAt());
	}

	@Test
	void acceptsCorrectStandaloneCodeBelowTheFailedAttemptLimit() {
		VerificationCode code = code("123456", Instant.now().plusSeconds(300));
		stubStandaloneCode(code);

		for (int attempt = 0; attempt < 4; attempt++) {
			assertInvalidVerification("user@example.com", "654321");
		}
		passwordResetService.verifyCode(new PasswordResetCodeVerificationRequest("user@example.com", "123456"));

		assertEquals(4, code.getFailedAttempts());
		assertNull(code.getUsedAt());
	}

	@Test
	void exhaustsCodeOnFifthIncorrectAttemptAndNeverIncrementsAgain() {
		VerificationCode code = code("123456", Instant.now().plusSeconds(300));
		stubStandaloneCode(code);

		for (int attempt = 0; attempt < 5; attempt++) {
			assertInvalidVerification("user@example.com", "654321");
		}
		assertEquals(5, code.getFailedAttempts());
		assertInvalidVerification("user@example.com", "123456");

		assertEquals(5, code.getFailedAttempts());
		assertNull(code.getUsedAt());
	}

	@Test
	void aNewCodeStartsWithAnIndependentFailedAttemptBudget() {
		VerificationCode exhaustedCode = code("654321", Instant.now().plusSeconds(300));
		for (int attempt = 0; attempt < 5; attempt++) {
			exhaustedCode.incrementFailedAttempts();
		}
		VerificationCode freshCode = code("123456", Instant.now().plusSeconds(300));
		stubStandaloneCode(freshCode);

		passwordResetService.verifyCode(new PasswordResetCodeVerificationRequest("user@example.com", "123456"));

		assertEquals(5, exhaustedCode.getFailedAttempts());
		assertEquals(0, freshCode.getFailedAttempts());
	}

	@Test
	void resetsPasswordConsumesLatestCodeRevokesSessionsAndPreservesAccountFields() {
		UserAccount user = user(UserStatus.INACTIVE);
		VerificationCode code = code("123456", Instant.now().plusSeconds(300));
		when(users.findByEmailIgnoreCaseForUpdate("user@example.com")).thenReturn(Optional.of(user));
		when(codes.findTopByEmailAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc("user@example.com",
				VerificationPurpose.PASSWORD_RESET)).thenReturn(Optional.of(code));
		when(passwordEncoder.encode("Password1!")).thenReturn("new-bcrypt-hash");

		passwordResetService.resetPassword(new PasswordResetRequest("User@EXAMPLE.COM", "123456", "Password1!"));

		assertEquals("new-bcrypt-hash", user.getPasswordHash());
		assertEquals("user@example.com", user.getEmail());
		assertEquals("member", user.getUsername());
		assertEquals(UserRole.MEMBER, user.getRole());
		assertEquals(UserStatus.INACTIVE, user.getStatus());
		assertNotNull(code.getUsedAt());
		verify(refreshTokens).revokeAllByUserId(eq(user.getId()), any(Instant.class));
	}

	@Test
	void rejectsInvalidResetCodesBeforeChangingPassword() {
		UserAccount user = user(UserStatus.ACTIVE);
		VerificationCode code = code("123456", Instant.now().plusSeconds(300));
		when(users.findByEmailIgnoreCaseForUpdate("user@example.com")).thenReturn(Optional.of(user));
		when(codes.findTopByEmailAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc("user@example.com",
				VerificationPurpose.PASSWORD_RESET)).thenReturn(Optional.of(code));

		ApiException exception = assertThrows(ApiException.class, () -> passwordResetService.resetPassword(
				new PasswordResetRequest("user@example.com", "654321", "Password1!")));

		assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
		assertEquals(1, code.getFailedAttempts());
		verify(passwordEncoder, never()).encode(any());
		verify(refreshTokens, never()).revokeAllByUserId(any(), any());
	}

	private void stubStandaloneCode(VerificationCode code) {
		when(users.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user(UserStatus.ACTIVE)));
		when(codes.findTopByEmailAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc("user@example.com",
				VerificationPurpose.PASSWORD_RESET)).thenReturn(Optional.of(code));
	}

	private void assertInvalidVerification(String email, String verificationCode) {
		ApiException exception = assertThrows(ApiException.class, () -> passwordResetService.verifyCode(
				new PasswordResetCodeVerificationRequest(email, verificationCode)));
		assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
	}

	private UserAccount user(UserStatus status) {
		return new UserAccount("user@example.com", "member", "old-bcrypt-hash", UserRole.MEMBER, status);
	}

	private VerificationCode code(String value, Instant expiresAt) {
		return new VerificationCode("user@example.com", sha256(value), VerificationPurpose.PASSWORD_RESET, expiresAt, Instant.now());
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
