package com.fpt.ibom.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.fpt.ibom.auth.dto.AuthenticatedUser;
import com.fpt.ibom.auth.dto.ChangePasswordRequest;
import com.fpt.ibom.auth.dto.ChangeUsernameRequest;
import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.service.AccountSettingsService;
import com.fpt.ibom.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

class AccountSettingsServiceTest {

	private final UserAccountRepository users = org.mockito.Mockito.mock(UserAccountRepository.class);
	private final PasswordEncoder passwordEncoder = org.mockito.Mockito.mock(PasswordEncoder.class);
	private final AccountSettingsService accountSettingsService = new AccountSettingsService(users, passwordEncoder);

	@Test
	void changesPasswordWithCorrectCurrentPasswordWithoutChangingAccountFields() {
		UserAccount user = user();
		when(users.findById(1L)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("current-password", "old-bcrypt-hash")).thenReturn(true);
		when(passwordEncoder.encode("new-password")).thenReturn("new-bcrypt-hash");

		accountSettingsService.changePassword(1L, new ChangePasswordRequest("current-password", "new-password"));

		assertEquals("new-bcrypt-hash", user.getPasswordHash());
		assertEquals("user@example.com", user.getEmail());
		assertEquals("Member", user.getUsername());
		assertEquals(UserRole.MEMBER, user.getRole());
		assertEquals(UserStatus.ACTIVE, user.getStatus());
		verify(passwordEncoder).encode("new-password");
	}

	@Test
	void rejectsIncorrectCurrentPasswordWithoutChangingIt() {
		UserAccount user = user();
		when(users.findById(1L)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("incorrect-password", "old-bcrypt-hash")).thenReturn(false);

		ApiException exception = assertThrows(ApiException.class, () -> accountSettingsService.changePassword(1L,
				new ChangePasswordRequest("incorrect-password", "new-password")));

		assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
		assertEquals("Invalid credentials", exception.getMessage());
		assertEquals("old-bcrypt-hash", user.getPasswordHash());
		verify(passwordEncoder, never()).encode("new-password");
	}

	@Test
	void trimsUsernamePreservesCasingAndLeavesOtherAccountFieldsUnchanged() {
		UserAccount user = user();
		when(users.findById(1L)).thenReturn(Optional.of(user));
		when(users.existsByUsernameIgnoreCaseAndIdNot("NewName", 1L)).thenReturn(false);

		AuthenticatedUser result = accountSettingsService.changeUsername(1L, new ChangeUsernameRequest("  NewName  "));

		assertEquals("NewName", user.getUsername());
		assertEquals("NewName", result.username());
		assertEquals("user@example.com", user.getEmail());
		assertEquals("old-bcrypt-hash", user.getPasswordHash());
		assertEquals(UserRole.MEMBER, user.getRole());
		assertEquals(UserStatus.ACTIVE, user.getStatus());
		verify(users).flush();
	}

	@Test
	void permitsCaseOnlyUsernameChangeForCurrentAccount() {
		UserAccount user = user();
		when(users.findById(1L)).thenReturn(Optional.of(user));
		when(users.existsByUsernameIgnoreCaseAndIdNot("member", 1L)).thenReturn(false);

		accountSettingsService.changeUsername(1L, new ChangeUsernameRequest("member"));

		assertEquals("member", user.getUsername());
	}

	@Test
	void rejectsUsernameOwnedByAnotherAccount() {
		when(users.findById(1L)).thenReturn(Optional.of(user()));
		when(users.existsByUsernameIgnoreCaseAndIdNot("OtherMember", 1L)).thenReturn(true);

		ApiException exception = assertThrows(ApiException.class, () -> accountSettingsService.changeUsername(1L,
				new ChangeUsernameRequest("OtherMember")));

		assertEquals(HttpStatus.CONFLICT, exception.getStatus());
		assertEquals("Username is already registered", exception.getMessage());
		verify(users, never()).flush();
	}

	@Test
	void translatesConcurrentUsernameConflict() {
		when(users.findById(1L)).thenReturn(Optional.of(user()));
		when(users.existsByUsernameIgnoreCaseAndIdNot("NewName", 1L)).thenReturn(false);
		org.mockito.Mockito.doThrow(new DataIntegrityViolationException("duplicate")).when(users).flush();

		ApiException exception = assertThrows(ApiException.class, () -> accountSettingsService.changeUsername(1L,
				new ChangeUsernameRequest("NewName")));

		assertEquals(HttpStatus.CONFLICT, exception.getStatus());
	}

	private UserAccount user() {
		return new UserAccount("user@example.com", "Member", "old-bcrypt-hash", UserRole.MEMBER, UserStatus.ACTIVE);
	}
}
