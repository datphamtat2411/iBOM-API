package com.fpt.ibom.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.fpt.ibom.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.fpt.ibom.auth.service.MailService;

class RegistrationServiceTest {

	private final UserAccountRepository users = mock(UserAccountRepository.class);
	private final VerificationCodeRepository codes = mock(VerificationCodeRepository.class);
	private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
	private final MailService mailService = mock(MailService.class);
	private final RegistrationService registrationService = new RegistrationService(users, codes, passwordEncoder, mailService,
			"fsoft.com.vn,fpt.com.vn,fpt.com,gmail.com");

	@Test
	void requestsCodeForAllowedNormalizedEmailAndRoutesMailThroughMailService() {
		when(codes.countByEmailAndPurposeAndCreatedAtAfter(eq("user@gmail.com"), eq(VerificationPurpose.REGISTRATION), any()))
				.thenReturn(0L);

		registrationService.requestVerificationCode(new RegistrationCodeRequest(" User@GMAIL.COM "));

		ArgumentCaptor<VerificationCode> code = ArgumentCaptor.forClass(VerificationCode.class);
		verify(codes).save(code.capture());
		assertEquals(64, code.getValue().getCodeHash().length());
		verify(mailService).sendRegistrationVerificationCode(eq("user@gmail.com"), any());
	}

	@Test
	void rejectsDisallowedDomain() {
		ApiException exception = assertThrows(ApiException.class,
				() -> registrationService.requestVerificationCode(new RegistrationCodeRequest("user@example.com")));

		assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
		verify(mailService, never()).sendRegistrationVerificationCode(any(), any());
	}

	@Test
	void limitsCodeRequestsToFivePerHour() {
		when(codes.countByEmailAndPurposeAndCreatedAtAfter(any(), eq(VerificationPurpose.REGISTRATION), any())).thenReturn(5L);

		ApiException exception = assertThrows(ApiException.class,
				() -> registrationService.requestVerificationCode(new RegistrationCodeRequest("user@gmail.com")));

		assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatus());
	}

	@Test
	void rejectsExpiredOrUsedVerificationCode() {
		VerificationCode expired = verificationCode("user@gmail.com", "123456", Instant.now().minusSeconds(1));
		when(codes.findFirstByEmailAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc("user@gmail.com", VerificationPurpose.REGISTRATION))
				.thenReturn(Optional.of(expired));

		assertInvalidCode();

		VerificationCode used = verificationCode("user@gmail.com", "123456", Instant.now().plusSeconds(300));
		used.use(Instant.now());
		when(codes.findFirstByEmailAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc("user@gmail.com", VerificationPurpose.REGISTRATION))
				.thenReturn(Optional.empty());
		assertInvalidCode();
	}

	@Test
	void rejectsInvalidVerificationCode() {
		when(codes.findFirstByEmailAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc("user@gmail.com", VerificationPurpose.REGISTRATION))
				.thenReturn(Optional.of(verificationCode("user@gmail.com", "654321", Instant.now().plusSeconds(300))));

		assertInvalidCode();
	}

	@Test
	void rejectsCaseInsensitiveDuplicateEmailAndUsername() {
		when(users.existsByEmailIgnoreCase("user@gmail.com")).thenReturn(true);
		ApiException emailException = assertThrows(ApiException.class, () -> registrationService.register(request()));
		assertEquals(HttpStatus.CONFLICT, emailException.getStatus());

		when(users.existsByEmailIgnoreCase("user@gmail.com")).thenReturn(false);
		when(users.existsByUsernameIgnoreCase("member")).thenReturn(true);
		ApiException usernameException = assertThrows(ApiException.class, () -> registrationService.register(request()));
		assertEquals(HttpStatus.CONFLICT, usernameException.getStatus());
	}

	@Test
	void createsActiveMemberWithEncodedPasswordAndConsumesCode() {
		VerificationCode code = verificationCode("user@gmail.com", "123456", Instant.now().plusSeconds(300));
		when(codes.findFirstByEmailAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc("user@gmail.com", VerificationPurpose.REGISTRATION))
				.thenReturn(Optional.of(code));
		when(passwordEncoder.encode("Password1!")).thenReturn("bcrypt-hash");

		registrationService.register(request());

		ArgumentCaptor<UserAccount> user = ArgumentCaptor.forClass(UserAccount.class);
		verify(users).saveAndFlush(user.capture());
		assertEquals("user@gmail.com", user.getValue().getEmail());
		assertEquals("bcrypt-hash", user.getValue().getPasswordHash());
		assertEquals(UserRole.MEMBER, user.getValue().getRole());
		assertEquals(UserStatus.ACTIVE, user.getValue().getStatus());
		assertEquals(false, code.getUsedAt() == null);
	}

	private void assertInvalidCode() {
		ApiException exception = assertThrows(ApiException.class, () -> registrationService.register(request()));
		assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
	}

	private RegistrationRequest request() {
		return new RegistrationRequest("User@GMAIL.COM", "member", "Password1!", "123456");
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
