package com.fpt.ibom.auth;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.security.UserAccountJwtAuthenticationConverter;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

class UserAccountJwtAuthenticationConverterTest {

	private final UserAccountRepository users = mock(UserAccountRepository.class);
	private final UserAccountJwtAuthenticationConverter converter = new UserAccountJwtAuthenticationConverter(users);

	@Test
	void acceptsActiveAccountWhenAuthenticationVersionsMatch() {
		UserAccount user = account(UserStatus.ACTIVE);
		user.invalidateAuthenticationState();
		when(users.findById(1L)).thenReturn(Optional.of(user));

		assertNotNull(converter.convert(token(1L)));
	}

	@Test
	void rejectsActiveAccountWhenAuthenticationVersionsDoNotMatch() {
		UserAccount user = account(UserStatus.ACTIVE);
		user.invalidateAuthenticationState();
		when(users.findById(1L)).thenReturn(Optional.of(user));

		assertThrows(InvalidBearerTokenException.class, () -> converter.convert(token(0L)));
	}

	@Test
	void treatsMissingAuthenticationVersionAsZeroForLegacyToken() {
		when(users.findById(1L)).thenReturn(Optional.of(account(UserStatus.ACTIVE)));

		assertNotNull(converter.convert(legacyToken()));
	}

	private UserAccount account(UserStatus status) {
		return new UserAccount("user@example.com", "member", "hash", UserRole.MEMBER, status);
	}

	private Jwt token(long authenticationVersion) {
		return Jwt.withTokenValue("access-token")
				.header("alg", "HS256")
				.subject("1")
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(900))
				.claim("email", "user@example.com")
				.claim("username", "member")
				.claim("role", UserRole.MEMBER.name())
				.claim("auth_version", authenticationVersion)
				.build();
	}

	private Jwt legacyToken() {
		return Jwt.withTokenValue("legacy-access-token")
				.header("alg", "HS256")
				.subject("1")
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(900))
				.claim("email", "user@example.com")
				.claim("username", "member")
				.claim("role", UserRole.MEMBER.name())
				.build();
	}
}
