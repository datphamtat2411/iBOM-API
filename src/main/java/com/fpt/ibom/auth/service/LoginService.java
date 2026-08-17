package com.fpt.ibom.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import com.fpt.ibom.auth.dto.AuthenticatedUser;
import com.fpt.ibom.auth.dto.LoginRequest;
import com.fpt.ibom.auth.entity.RefreshToken;
import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.repository.RefreshTokenRepository;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.security.JwtService;
import com.fpt.ibom.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginService {

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
		UserAccount user = userAccountRepository.findByEmail(request.email())
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
		}
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new ApiException(HttpStatus.FORBIDDEN, "Account is inactive");
		}

		String refreshToken = generateRefreshToken();
		refreshTokenRepository.save(new RefreshToken(user, sha256(refreshToken), Instant.now().plusSeconds(refreshTokenTtlSeconds)));
		return new AuthenticationResult(jwtService.createAccessToken(user), refreshToken, refreshTokenTtlSeconds,
				new AuthenticatedUser(user.getId(), user.getEmail(), user.getUsername(), user.getRole()));
	}

	private String generateRefreshToken() {
		byte[] bytes = new byte[32];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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
