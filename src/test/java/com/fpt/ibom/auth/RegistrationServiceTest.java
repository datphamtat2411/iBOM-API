package com.fpt.ibom.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Optional;

import com.fpt.ibom.auth.dto.RegistrationCodeRequest;
import com.fpt.ibom.auth.dto.RegistrationRequest;
import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.entity.VerificationCode;
import com.fpt.ibom.auth.entity.VerificationPurpose;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.repository.VerificationCodeRepository;
import com.fpt.ibom.auth.service.RegistrationService;
import com.fpt.ibom.auth.service.UserAccountCreationService;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import com.fpt.ibom.auth.service.MailService;

class RegistrationServiceTest {

	private final UserAccountRepository users = mock(UserAccountRepository.class);
	private final VerificationCodeRepository codes = mock(VerificationCodeRepository.class);
	private final UserAccountCreationService accountCreationService = mock(UserAccountCreationService.class);
	private final MailService mailService = mock(MailService.class);
	private final RegistrationService registrationService = new RegistrationService(users, codes, accountCreationService,
			mailService);

	@BeforeEach
	void preparesNormalizedAccountDetails() {
		when(accountCreationService.normalizeAndValidateEmail(anyString())).thenAnswer(invocation ->
				invocation.getArgument(0, String.class).trim().toLowerCase(java.util.Locale.ROOT));
		when(accountCreationService.prepare(anyString(), anyString())).thenAnswer(invocation ->
				new UserAccountCreationService.PreparedAccount(
						invocation.getArgument(0, String.class).trim().toLowerCase(java.util.Locale.ROOT),
						invocation.getArgument(1, String.class).trim()));
	}

	@Test
	void requestsCodeForAllowedNormalizedEmailAndRoutesMailThroughMailService() {
		when(codes.countByEmailAndPurposeAndCreatedAtAfter(eq("user@gmail.com"), eq(VerificationPurpose.REGISTRATION), any()))
				.thenReturn(0L);

		registrationService.requestVerificationCode(new RegistrationCodeRequest(" User@GMAIL.COM "));

		ArgumentCaptor<VerificationCode> code = ArgumentCaptor.forClass(VerificationCode.class);
		verify(codes).save(code.capture());
		assertEquals(64, code.getValue().getCodeHash().length());
		assertEquals(0, code.getValue().getFailedAttempts());
		verify(mailService).sendRegistrationVerificationCode(eq("user@gmail.com"), any());
	}

	@Test
	void rejectsDisallowedDomain() {
		when(accountCreationService.normalizeAndValidateEmail("user@example.com"))
				.thenThrow(new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.AUTH_EMAIL_DOMAIN_NOT_ALLOWED,
						"Email domain is not allowed"));
		ApiException exception = assertThrows(ApiException.class,
				() -> registrationService.requestVerificationCode(new RegistrationCodeRequest("user@example.com")));

		assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
		assertEquals(ErrorCode.AUTH_EMAIL_DOMAIN_NOT_ALLOWED, exception.getErrorCode());
		verify(mailService, never()).sendRegistrationVerificationCode(any(), any());
	}

	@Test
	void silentlyCompletesForCaseInsensitiveExistingEmailWithoutCreatingOrSendingCode() {
		when(users.existsByEmailIgnoreCase("user@gmail.com")).thenReturn(true);

		assertDoesNotThrow(
				() -> registrationService.requestVerificationCode(new RegistrationCodeRequest(" User@GMAIL.COM ")));
		verify(codes, never()).save(any());
		verify(mailService, never()).sendRegistrationVerificationCode(any(), any());
	}

	@Test
	void limitsCodeRequestsToFivePerHour() {
		when(codes.countByEmailAndPurposeAndCreatedAtAfter(any(), eq(VerificationPurpose.REGISTRATION), any())).thenReturn(5L);

		ApiException exception = assertThrows(ApiException.class,
				() -> registrationService.requestVerificationCode(new RegistrationCodeRequest("user@gmail.com")));

		assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatus());
		assertEquals(ErrorCode.AUTH_VERIFICATION_CODE_REQUEST_LIMIT_REACHED, exception.getErrorCode());
	}

	@Test
	void rejectsExpiredOrUsedVerificationCode() {
		VerificationCode expired = verificationCode("user@gmail.com", "123456", Instant.now().minusSeconds(1));
		when(codes.findTopByEmailAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc("user@gmail.com", VerificationPurpose.REGISTRATION))
				.thenReturn(Optional.of(expired));

		assertInvalidCode();

		VerificationCode used = verificationCode("user@gmail.com", "123456", Instant.now().plusSeconds(300));
		used.use(Instant.now());
		when(codes.findTopByEmailAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc("user@gmail.com", VerificationPurpose.REGISTRATION))
				.thenReturn(Optional.empty());
		assertInvalidCode();
	}

	@Test
	void incrementsFailedAttemptsForInvalidVerificationCode() {
		VerificationCode code = verificationCode("user@gmail.com", "654321", Instant.now().plusSeconds(300));
		when(codes.findTopByEmailAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc("user@gmail.com", VerificationPurpose.REGISTRATION))
				.thenReturn(Optional.of(code));

		assertInvalidCode();
		assertEquals(1, code.getFailedAttempts());
	}

	@Test
	void capsFailedAttemptsAtFiveAndRejectsCorrectCodeAfterExhaustion() {
		VerificationCode code = verificationCode("user@gmail.com", "654321", Instant.now().plusSeconds(300));
		when(codes.findTopByEmailAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc("user@gmail.com", VerificationPurpose.REGISTRATION))
				.thenReturn(Optional.of(code));

		for (int attempt = 1; attempt <= 5; attempt++) {
			assertInvalidCode();
			assertEquals(attempt, code.getFailedAttempts());
		}
		assertInvalidCode();
		assertEquals(5, code.getFailedAttempts());

		ApiException exception = assertThrows(ApiException.class,
				() -> registrationService.register(request("654321")));
		assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
		assertEquals(ErrorCode.AUTH_INVALID_OR_EXPIRED_VERIFICATION_CODE, exception.getErrorCode());
		assertEquals(5, code.getFailedAttempts());
		verify(users, never()).saveAndFlush(any());
	}

	@Test
	void rejectsCaseInsensitiveDuplicateEmailAndUsername() {
		when(accountCreationService.prepare("User@GMAIL.COM", "member"))
				.thenThrow(new ApiException(HttpStatus.CONFLICT, ErrorCode.AUTH_EMAIL_ALREADY_REGISTERED,
						"Email is already registered"));
		ApiException emailException = assertThrows(ApiException.class, () -> registrationService.register(request()));
		assertEquals(HttpStatus.CONFLICT, emailException.getStatus());
		assertEquals(ErrorCode.AUTH_EMAIL_ALREADY_REGISTERED, emailException.getErrorCode());

		reset(accountCreationService);
		when(accountCreationService.prepare("User@GMAIL.COM", "member"))
				.thenThrow(new ApiException(HttpStatus.CONFLICT, ErrorCode.AUTH_USERNAME_ALREADY_REGISTERED,
						"Username is already registered"));
		ApiException usernameException = assertThrows(ApiException.class, () -> registrationService.register(request()));
		assertEquals(HttpStatus.CONFLICT, usernameException.getStatus());
		assertEquals(ErrorCode.AUTH_USERNAME_ALREADY_REGISTERED, usernameException.getErrorCode());
	}

	@Test
	void createsActiveMemberWithEncodedPasswordAndConsumesCode() {
		VerificationCode code = verificationCode("user@gmail.com", "123456", Instant.now().plusSeconds(300));
		for (int attempt = 0; attempt < 4; attempt++) {
			code.incrementFailedAttempts();
		}
		when(codes.findTopByEmailAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc("user@gmail.com", VerificationPurpose.REGISTRATION))
				.thenReturn(Optional.of(code));
		when(accountCreationService.create(any(UserAccountCreationService.PreparedAccount.class), eq("Password1!"),
				eq(UserRole.MEMBER))).thenReturn(new UserAccount("user@gmail.com", "member", "bcrypt-hash",
					UserRole.MEMBER, UserStatus.ACTIVE));

		registrationService.register(request());

		verify(accountCreationService).create(
				new UserAccountCreationService.PreparedAccount("user@gmail.com", "member"), "Password1!",
				UserRole.MEMBER);
		assertEquals(4, code.getFailedAttempts());
		assertEquals(false, code.getUsedAt() == null);
	}

	@Test
	void doesNotConsumeCodeWhenAccountCreationFails() {
		VerificationCode code = verificationCode("user@gmail.com", "123456", Instant.now().plusSeconds(300));
		when(codes.findTopByEmailAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc("user@gmail.com", VerificationPurpose.REGISTRATION))
				.thenReturn(Optional.of(code));
		when(accountCreationService.create(any(UserAccountCreationService.PreparedAccount.class), eq("Password1!"),
				eq(UserRole.MEMBER))).thenThrow(new ApiException(HttpStatus.CONFLICT,
					ErrorCode.AUTH_EMAIL_OR_USERNAME_ALREADY_REGISTERED,
					"Email or username is already registered"));

		ApiException exception = assertThrows(ApiException.class, () -> registrationService.register(request()));

		assertEquals(HttpStatus.CONFLICT, exception.getStatus());
		assertEquals(ErrorCode.AUTH_EMAIL_OR_USERNAME_ALREADY_REGISTERED, exception.getErrorCode());
		assertNull(code.getUsedAt());
	}

	private void assertInvalidCode() {
		ApiException exception = assertThrows(ApiException.class, () -> registrationService.register(request()));
		assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
		assertEquals(ErrorCode.AUTH_INVALID_OR_EXPIRED_VERIFICATION_CODE, exception.getErrorCode());
	}

	private RegistrationRequest request() {
		return request("123456");
	}

	private RegistrationRequest request(String verificationCode) {
		return new RegistrationRequest("User@GMAIL.COM", "member", "Password1!", verificationCode);
	}

	private VerificationCode verificationCode(String email, String code, Instant expiresAt) {
		return new VerificationCode(email, sha256(code), VerificationPurpose.REGISTRATION, expiresAt, Instant.now());
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
