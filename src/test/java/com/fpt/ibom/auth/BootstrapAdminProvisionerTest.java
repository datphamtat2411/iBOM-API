package com.fpt.ibom.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.fpt.ibom.auth.config.BootstrapAdminProperties;
import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.service.BootstrapAdminProvisioner;
import com.fpt.ibom.auth.service.UserAccountCreationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

class BootstrapAdminProvisionerTest {

	@Test
	void disabledBootstrapDoesNotValidateOrProvision() throws Exception {
		UserAccountRepository users = mock(UserAccountRepository.class);
		UserAccountCreationService creationService = mock(UserAccountCreationService.class);
		BootstrapAdminProperties properties = properties(false, "", "", "");

		provisioner(users, creationService, properties).run(new DefaultApplicationArguments());

		verifyNoInteractions(users, creationService);
	}

	@Test
	void enabledValidConfigurationCreatesOneActiveAdminWithEncodedPassword() throws Exception {
		UserAccountRepository users = mock(UserAccountRepository.class);
		PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
		when(users.findByEmailIgnoreCase("admin@gmail.com")).thenReturn(Optional.empty());
		when(users.findByUsernameIgnoreCase("initial-admin")).thenReturn(Optional.empty());
		when(users.existsByEmailIgnoreCase("admin@gmail.com")).thenReturn(false);
		when(users.existsByUsernameIgnoreCase("initial-admin")).thenReturn(false);
		when(passwordEncoder.encode("Password1!")).thenReturn("encoded-password");
		when(users.saveAndFlush(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
		UserAccountCreationService creationService = new UserAccountCreationService(users, passwordEncoder,
				"gmail.com");

		provisioner(users, creationService, properties(true, " Admin@GMAIL.COM ", " initial-admin ", "Password1!"))
				.run(new DefaultApplicationArguments());

		ArgumentCaptor<UserAccount> account = ArgumentCaptor.forClass(UserAccount.class);
		verify(users).saveAndFlush(account.capture());
		assertEquals("admin@gmail.com", account.getValue().getEmail());
		assertEquals("initial-admin", account.getValue().getUsername());
		assertEquals("encoded-password", account.getValue().getPasswordHash());
		assertFalse("Password1!".equals(account.getValue().getPasswordHash()));
		assertEquals(UserRole.ADMIN, account.getValue().getRole());
		assertEquals(UserStatus.ACTIVE, account.getValue().getStatus());
		verify(passwordEncoder).encode("Password1!");
	}

	@Test
	void existingMatchingAccountIsUntouched() throws Exception {
		UserAccountRepository users = mock(UserAccountRepository.class);
		UserAccountCreationService creationService = mock(UserAccountCreationService.class);
		UserAccount existing = new UserAccount("admin@gmail.com", "initial-admin", "existing-hash",
				UserRole.MEMBER, UserStatus.INACTIVE);
		when(creationService.normalizeAndValidateEmail("admin@gmail.com")).thenReturn("admin@gmail.com");
		when(creationService.normalizeAndValidateUsername("initial-admin")).thenReturn("initial-admin");
		when(users.findByEmailIgnoreCase("admin@gmail.com")).thenReturn(Optional.of(existing));
		when(users.findByUsernameIgnoreCase("initial-admin")).thenReturn(Optional.of(existing));

		provisioner(users, creationService, properties(true, "admin@gmail.com", "initial-admin", "Password1!"))
				.run(new DefaultApplicationArguments());

		assertEquals("existing-hash", existing.getPasswordHash());
		assertEquals(UserRole.MEMBER, existing.getRole());
		assertEquals(UserStatus.INACTIVE, existing.getStatus());
		verify(creationService, never()).create(anyString(), anyString(), anyString(), any());
		verify(users, never()).saveAndFlush(any());
	}

	@Test
	void repeatedStartupRemainsIdempotent() throws Exception {
		UserAccountRepository users = mock(UserAccountRepository.class);
		PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
		AtomicReference<UserAccount> persisted = new AtomicReference<>();
		when(users.findByEmailIgnoreCase(anyString())).thenAnswer(invocation -> Optional.ofNullable(persisted.get()));
		when(users.findByUsernameIgnoreCase(anyString())).thenAnswer(invocation -> Optional.ofNullable(persisted.get()));
		when(users.existsByEmailIgnoreCase(anyString())).thenAnswer(invocation -> persisted.get() != null);
		when(users.existsByUsernameIgnoreCase(anyString())).thenAnswer(invocation -> persisted.get() != null);
		when(passwordEncoder.encode("Password1!")).thenReturn("encoded-password");
		when(users.saveAndFlush(any(UserAccount.class))).thenAnswer(invocation -> {
			UserAccount account = invocation.getArgument(0);
			persisted.set(account);
			return account;
		});
		UserAccountCreationService creationService = new UserAccountCreationService(users, passwordEncoder,
				"gmail.com");
		BootstrapAdminProvisioner provisioner = provisioner(users, creationService,
				properties(true, "admin@gmail.com", "initial-admin", "Password1!"));

		provisioner.run(new DefaultApplicationArguments());
		provisioner.run(new DefaultApplicationArguments());

		verify(users, times(1)).saveAndFlush(any(UserAccount.class));
		assertEquals(UserRole.ADMIN, persisted.get().getRole());
		assertEquals(UserStatus.ACTIVE, persisted.get().getStatus());
	}

	@Test
	void conflictingExistingEmailAndUsernameFailsWithoutWriting() throws Exception {
		UserAccountRepository users = mock(UserAccountRepository.class);
		UserAccountCreationService creationService = mock(UserAccountCreationService.class);
		when(creationService.normalizeAndValidateEmail("admin@gmail.com")).thenReturn("admin@gmail.com");
		when(creationService.normalizeAndValidateUsername("initial-admin")).thenReturn("initial-admin");
		when(users.findByEmailIgnoreCase("admin@gmail.com")).thenReturn(Optional.of(
				new UserAccount("admin@gmail.com", "other-user", "hash", UserRole.MEMBER, UserStatus.ACTIVE)));
		when(users.findByUsernameIgnoreCase("initial-admin")).thenReturn(Optional.of(
				new UserAccount("other@gmail.com", "initial-admin", "hash", UserRole.MANAGER, UserStatus.ACTIVE)));

		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> provisioner(users, creationService,
						properties(true, "admin@gmail.com", "initial-admin", "Password1!")).run(new DefaultApplicationArguments()));

		assertTrue(exception.getMessage().contains("conflicts"));
		verify(creationService, never()).create(anyString(), anyString(), anyString(), any());
		verify(users, never()).saveAndFlush(any());
	}

	@Test
	void singleExistingIdentityFailsWithoutWriting() throws Exception {
		UserAccountRepository users = mock(UserAccountRepository.class);
		UserAccountCreationService creationService = mock(UserAccountCreationService.class);
		when(creationService.normalizeAndValidateEmail("admin@gmail.com")).thenReturn("admin@gmail.com");
		when(creationService.normalizeAndValidateUsername("initial-admin")).thenReturn("initial-admin");
		when(users.findByEmailIgnoreCase("admin@gmail.com")).thenReturn(Optional.of(
				new UserAccount("admin@gmail.com", "other-user", "hash", UserRole.MEMBER, UserStatus.ACTIVE)));
		when(users.findByUsernameIgnoreCase("initial-admin")).thenReturn(Optional.empty());

		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> provisioner(users, creationService,
						properties(true, "admin@gmail.com", "initial-admin", "Password1!")).run(new DefaultApplicationArguments()));

		assertTrue(exception.getMessage().contains("conflicts"));
		verify(creationService, never()).create(anyString(), anyString(), anyString(), any());
		verify(users, never()).saveAndFlush(any());
	}

	@Test
	void missingEnabledConfigurationFailsWithoutExposingPassword() {
		UserAccountRepository users = mock(UserAccountRepository.class);
		UserAccountCreationService creationService = mock(UserAccountCreationService.class);
		String password = "SecretPassword1!";

		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> provisioner(users, creationService, properties(true, "admin@gmail.com", "initial-admin", " "))
						.run(new DefaultApplicationArguments()));

		assertTrue(exception.getMessage().contains("admin-password"));
		assertFalse(exception.getMessage().contains(password));
		verifyNoInteractions(users, creationService);
	}

	@Test
	void disallowedEmailDomainReusesAccountValidationAndFailsClearly() {
		UserAccountRepository users = mock(UserAccountRepository.class);
		PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
		UserAccountCreationService creationService = new UserAccountCreationService(users, passwordEncoder, "gmail.com");

		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> provisioner(users, creationService,
						properties(true, "admin@example.com", "initial-admin", "Password1!"))
						.run(new DefaultApplicationArguments()));

		assertTrue(exception.getMessage().contains("admin-email"));
		assertFalse(exception.getMessage().contains("Password1!"));
		verifyNoInteractions(users, passwordEncoder);
	}

	@Test
	void invalidPasswordReusesStrongPasswordValidationAndFailsClearly() {
		UserAccountRepository users = mock(UserAccountRepository.class);
		PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
		UserAccountCreationService creationService = new UserAccountCreationService(users, passwordEncoder, "gmail.com");

		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> provisioner(users, creationService,
						properties(true, "admin@gmail.com", "initial-admin", "weak"))
						.run(new DefaultApplicationArguments()));

		assertTrue(exception.getMessage().contains("admin-password"));
		assertFalse(exception.getMessage().contains("weak"));
		verifyNoInteractions(users, passwordEncoder);
	}

	@Test
	void accountCreationRaceIsReportedWithoutExposingPassword() throws Exception {
		UserAccountRepository users = mock(UserAccountRepository.class);
		UserAccountCreationService creationService = mock(UserAccountCreationService.class);
		when(creationService.normalizeAndValidateEmail("admin@gmail.com")).thenReturn("admin@gmail.com");
		when(creationService.normalizeAndValidateUsername("initial-admin")).thenReturn("initial-admin");
		when(users.findByEmailIgnoreCase("admin@gmail.com")).thenReturn(Optional.empty());
		when(users.findByUsernameIgnoreCase("initial-admin")).thenReturn(Optional.empty());
		when(creationService.create("admin@gmail.com", "initial-admin", "Password1!", UserRole.ADMIN))
				.thenThrow(new com.fpt.ibom.exception.ApiException(org.springframework.http.HttpStatus.CONFLICT,
						com.fpt.ibom.exception.ErrorCode.AUTH_EMAIL_OR_USERNAME_ALREADY_REGISTERED,
						"Email or username is already registered"));

		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> provisioner(users, creationService,
						properties(true, "admin@gmail.com", "initial-admin", "Password1!"))
						.run(new DefaultApplicationArguments()));

		assertTrue(exception.getMessage().contains("uniqueness"));
		assertFalse(exception.getMessage().contains("Password1!"));
	}

	private BootstrapAdminProvisioner provisioner(UserAccountRepository users,
			UserAccountCreationService creationService, BootstrapAdminProperties properties) {
		return new BootstrapAdminProvisioner(users, creationService, properties);
	}

	private BootstrapAdminProperties properties(boolean enabled, String email, String username, String password) {
		BootstrapAdminProperties properties = new BootstrapAdminProperties();
		properties.setEnabled(enabled);
		properties.setAdminEmail(email);
		properties.setAdminUsername(username);
		properties.setAdminPassword(password);
		return properties;
	}
}
