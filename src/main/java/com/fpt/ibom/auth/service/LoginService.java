package com.fpt.ibom.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

import com.fpt.ibom.auth.dto.AuthenticatedUser;
import com.fpt.ibom.auth.dto.LoginRequest;
import com.fpt.ibom.auth.entity.RefreshToken;
import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.repository.RefreshTokenRepository;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.security.JwtService;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginService {

	private static final String DUMMY_PASSWORD_HASH = "$2a$12$C6UzMDM.H6dfI/f/IKcEeOeGxM1M8fM6mR8Xk1o9q0fPq8L9Qv7yW";

	private final UserAccountRepository userAccountRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final long refreshTokenTtlSeconds;
	private final SecureRandom secureRandom = new SecureRandom();

	public LoginService(UserAccountRepository userAccountRepository, RefreshTokenRepository refreshTokenRepository,
			PasswordEncoder passwordEncoder, JwtService jwtService,
			@Value("${app.auth.refresh-token-ttl-seconds}") long refreshTokenTtlSeconds) {
		this.userAccountRepository = userAccountRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
	}

	@Transactional
	public AuthenticationResult authenticate(LoginRequest request) {
		var userOptional = userAccountRepository.findByEmailIgnoreCase(normalizeEmail(request.email()));
		if (userOptional.isEmpty()) {
			passwordEncoder.matches(request.password(), DUMMY_PASSWORD_HASH);
			throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_INVALID_CREDENTIALS, "Invalid credentials");
		}
		UserAccount user = userOptional.get();
		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_INVALID_CREDENTIALS, "Invalid credentials");
		}
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.AUTH_ACCOUNT_INACTIVE, "Account is inactive");
		}

		return createAuthenticationResult(user);
	}

	@Transactional
	public AuthenticationResult refresh(String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			throw invalidRefreshToken();
		}
		RefreshToken session = refreshTokenRepository.findByTokenHashForUpdate(sha256(refreshToken))
				.orElseThrow(this::invalidRefreshToken);
		if (session.getExpiresAt().isBefore(Instant.now()) || session.getRevokedAt() != null
				|| session.getUser().getStatus() != UserStatus.ACTIVE) {
			throw invalidRefreshToken();
		}
		session.revoke();
		return createAuthenticationResult(session.getUser());
	}

	@Transactional
	public void logout(String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			return;
		}
		refreshTokenRepository.findByTokenHashForUpdate(sha256(refreshToken))
				.filter(session -> session.getRevokedAt() == null)
				.ifPresent(RefreshToken::revoke);
	}

	private AuthenticationResult createAuthenticationResult(UserAccount user) {
		String refreshToken = generateRefreshToken();
		refreshTokenRepository.save(new RefreshToken(user, sha256(refreshToken), Instant.now().plusSeconds(refreshTokenTtlSeconds)));
		return new AuthenticationResult(jwtService.createAccessToken(user), refreshToken, refreshTokenTtlSeconds,
				new AuthenticatedUser(user.getId(), user.getEmail(), user.getUsername(), user.getRole()));
	}

	private ApiException invalidRefreshToken() {
		return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_INVALID_REFRESH_TOKEN, "Invalid refresh token");
	}

	private String generateRefreshToken() {
		byte[] bytes = new byte[32];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private String sha256(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(digest);
		} catch (Exception exception) {
			throw new IllegalStateException("Unable to hash refresh token", exception);
		}
	}

	public record AuthenticationResult(String accessToken, String refreshToken, long refreshTokenTtlSeconds,
			AuthenticatedUser user) {
	}
}
