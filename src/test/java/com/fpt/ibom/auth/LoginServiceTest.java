package com.fpt.ibom.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
	private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);
	private final LoginService loginService = new LoginService(users, refreshTokens, passwordEncoder, jwtService, 604800);

	@Test
	void authenticatesActiveUserAndCreatesRefreshSession() {
		String passwordHash = passwordEncoder.encode("correct-password");
		assertEquals("$2a$12$", passwordHash.substring(0, 7));
		UserAccount user = new UserAccount("user@example.com", "member", passwordHash,
				UserRole.MEMBER, UserStatus.ACTIVE);
		when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user));
		when(jwtService.createAccessToken(user)).thenReturn("access-token");

		LoginService.AuthenticationResult result = loginService.authenticate(new LoginRequest("user@example.com", "correct-password"));

		assertEquals("access-token", result.accessToken());
		assertEquals(604800, result.refreshTokenTtlSeconds());
		assertEquals(UserRole.MEMBER, result.user().role());
		verify(refreshTokens).save(any(RefreshToken.class));
	}

	@Test
	void rejectsInvalidCredentials() {
		when(users.findByEmail("user@example.com")).thenReturn(Optional.empty());

		ApiException exception = assertThrows(ApiException.class,
				() -> loginService.authenticate(new LoginRequest("user@example.com", "incorrect-password")));

		assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
		assertEquals("Invalid credentials", exception.getMessage());
	}

	@Test
	void returnsTheSameResponseForIncorrectPassword() {
		UserAccount user = new UserAccount("user@example.com", "member", passwordEncoder.encode("correct-password"),
				UserRole.MEMBER, UserStatus.ACTIVE);
		when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user));

		ApiException exception = assertThrows(ApiException.class,
				() -> loginService.authenticate(new LoginRequest("user@example.com", "incorrect-password")));

		assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
		assertEquals("Invalid credentials", exception.getMessage());
	}

	@Test
	void rejectsInactiveUserAfterCredentialVerification() {
		UserAccount user = new UserAccount("user@example.com", "member", passwordEncoder.encode("correct-password"),
				UserRole.MEMBER, UserStatus.INACTIVE);
		when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user));

		ApiException exception = assertThrows(ApiException.class,
				() -> loginService.authenticate(new LoginRequest("user@example.com", "correct-password")));

		assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
	}
}
