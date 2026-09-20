package com.fpt.ibom.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.service.UserAccountCreationService;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserAccountCreationServiceTest {

	private final UserAccountRepository users = mock(UserAccountRepository.class);
	private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
	private final UserAccountCreationService service = new UserAccountCreationService(users, passwordEncoder,
			"fsoft.com.vn,fpt.com.vn,fpt.com,gmail.com");

	@Test
	void normalizesEmailAndUsernameEncodesPasswordAndForcesActiveStatus() {
		when(passwordEncoder.encode("Password1!")).thenReturn("bcrypt-hash");
		when(users.saveAndFlush(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

		UserAccountCreationService.PreparedAccount prepared = service.prepare(" User@GMAIL.COM ", " managed-user ");
		service.create(prepared, "Password1!", UserRole.ADMIN);

		ArgumentCaptor<UserAccount> account = ArgumentCaptor.forClass(UserAccount.class);
		verify(users).saveAndFlush(account.capture());
		assertEquals("user@gmail.com", account.getValue().getEmail());
		assertEquals("managed-user", account.getValue().getUsername());
		assertEquals("bcrypt-hash", account.getValue().getPasswordHash());
		assertEquals(UserRole.ADMIN, account.getValue().getRole());
		assertEquals(UserStatus.ACTIVE, account.getValue().getStatus());
		verify(passwordEncoder).encode("Password1!");
	}

	@ParameterizedTest
	@EnumSource(UserRole.class)
	void supportsEveryManagedRole(UserRole role) {
		when(passwordEncoder.encode("Password1!")).thenReturn("hash");
		when(users.saveAndFlush(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

		UserAccountCreationService.PreparedAccount prepared = service.prepare(role.name() + "@gmail.com", role.name());
		UserAccount created = service.create(prepared, "Password1!", role);

		assertEquals(role, created.getRole());
		assertEquals(UserStatus.ACTIVE, created.getStatus());
	}

	@Test
	void rejectsDisallowedEmailDomainBeforeRepositoryAccess() {
		ApiException exception = assertThrows(ApiException.class,
				() -> service.prepare("user@example.com", "managed-user"));

		assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
		assertEquals(ErrorCode.AUTH_EMAIL_DOMAIN_NOT_ALLOWED, exception.getErrorCode());
		verify(users, never()).existsByEmailIgnoreCase(any());
	}

	@Test
	void rejectsCaseInsensitiveDuplicateEmailAndUsername() {
		when(users.existsByEmailIgnoreCase("user@gmail.com")).thenReturn(true);
		ApiException emailException = assertThrows(ApiException.class,
				() -> service.prepare(" User@GMAIL.COM ", "managed-user"));
		assertEquals(ErrorCode.AUTH_EMAIL_ALREADY_REGISTERED, emailException.getErrorCode());

		when(users.existsByEmailIgnoreCase("user@gmail.com")).thenReturn(false);
		when(users.existsByUsernameIgnoreCase("managed-user")).thenReturn(true);
		ApiException usernameException = assertThrows(ApiException.class,
				() -> service.prepare("user@gmail.com", " managed-user "));
		assertEquals(ErrorCode.AUTH_USERNAME_ALREADY_REGISTERED, usernameException.getErrorCode());
	}

	@Test
	void translatesConcurrentPersistenceUniquenessFailure() {
		when(passwordEncoder.encode("Password1!")).thenReturn("hash");
		when(users.saveAndFlush(any(UserAccount.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

		UserAccountCreationService.PreparedAccount prepared = service.prepare("user@gmail.com", "managed-user");
		ApiException exception = assertThrows(ApiException.class,
				() -> service.create(prepared, "Password1!", UserRole.MEMBER));

		assertEquals(HttpStatus.CONFLICT, exception.getStatus());
		assertEquals(ErrorCode.AUTH_EMAIL_OR_USERNAME_ALREADY_REGISTERED, exception.getErrorCode());
	}
}
