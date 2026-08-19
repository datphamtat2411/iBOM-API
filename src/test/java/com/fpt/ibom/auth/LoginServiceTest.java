package com.fpt.ibom.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.fpt.ibom.auth.dto.LoginRequest;
import com.fpt.ibom.auth.entity.RefreshToken;
import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.repository.RefreshTokenRepository;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.security.JwtService;
import com.fpt.ibom.auth.service.LoginService;
import com.fpt.ibom.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class LoginServiceTest {

	private final UserAccountRepository users = mock(UserAccountRepository.class);
	private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
	private final JwtService jwtService = mock(JwtService.class);
	private final BCryptPasswordEncoder passwordEncoder = mock(BCryptPasswordEncoder.class);
	private final LoginService loginService = new LoginService(users, refreshTokens, passwordEncoder, jwtService, 604800);

	@Test
	void authenticatesActiveUserAndCreatesRefreshSession() {
		String passwordHash = "$2a$12$wJ8w4J0vM7fFh2cQ0k7YyO7Vh5L7s3W1n8z0j4hQxC3g6k2mP1a3e";
		UserAccount user = new UserAccount("user@example.com", "member", passwordHash,
				UserRole.MEMBER, UserStatus.ACTIVE);
		when(users.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("correct-password", passwordHash)).thenReturn(true);
		when(jwtService.createAccessToken(user)).thenReturn("access-token");

		LoginService.AuthenticationResult result = loginService.authenticate(new LoginRequest("user@example.com", "correct-password"));

		assertEquals("access-token", result.accessToken());
		assertEquals(604800, result.refreshTokenTtlSeconds());
		assertEquals(UserRole.MEMBER, result.user().role());
		verify(refreshTokens).save(any(RefreshToken.class));
	}

	@Test
	void rejectsUnknownEmailAndUsesDummyPasswordCheck() {
		when(users.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());
		when(passwordEncoder.matches("incorrect-password", "$2a$12$C6UzMDM.H6dfI/f/IKcEeOeGxM1M8fM6mR8Xk1o9q0fPq8L9Qv7yW")).thenReturn(true);

		ApiException exception = assertThrows(ApiException.class,
				() -> loginService.authenticate(new LoginRequest("user@example.com", "incorrect-password")));

		assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
		assertEquals("Invalid credentials", exception.getMessage());
		verify(passwordEncoder).matches("incorrect-password", "$2a$12$C6UzMDM.H6dfI/f/IKcEeOeGxM1M8fM6mR8Xk1o9q0fPq8L9Qv7yW");
	}

	@Test
	void rejectsExistingEmailWithWrongPasswordAndUsesStoredHash() {
		String passwordHash = "$2a$12$wJ8w4J0vM7fFh2cQ0k7YyO7Vh5L7s3W1n8z0j4hQxC3g6k2mP1a3e";
		UserAccount user = new UserAccount("user@example.com", "member", passwordHash,
				UserRole.MEMBER, UserStatus.ACTIVE);
		when(users.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("incorrect-password", passwordHash)).thenReturn(false);

		ApiException exception = assertThrows(ApiException.class,
				() -> loginService.authenticate(new LoginRequest("user@example.com", "incorrect-password")));

		assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
		assertEquals("Invalid credentials", exception.getMessage());
		verify(passwordEncoder).matches("incorrect-password", passwordHash);
	}

	@Test
	void rejectsInactiveUserAfterCredentialVerification() {
		String passwordHash = "$2a$12$wJ8w4J0vM7fFh2cQ0k7YyO7Vh5L7s3W1n8z0j4hQxC3g6k2mP1a3e";
		UserAccount user = new UserAccount("user@example.com", "member", passwordHash,
				UserRole.MEMBER, UserStatus.INACTIVE);
		when(users.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("correct-password", passwordHash)).thenReturn(true);

		ApiException exception = assertThrows(ApiException.class,
				() -> loginService.authenticate(new LoginRequest("user@example.com", "correct-password")));

		assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
	}

	@Test
	void normalizesEmailBeforeLookup() {
		when(users.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());

		assertThrows(ApiException.class,
				() -> loginService.authenticate(new LoginRequest(" User@Example.COM ", "incorrect-password")));

		verify(users).findByEmailIgnoreCase("user@example.com");
	}
}
