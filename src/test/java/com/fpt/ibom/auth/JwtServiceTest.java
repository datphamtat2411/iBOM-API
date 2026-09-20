package com.fpt.ibom.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.security.JwtService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

class JwtServiceTest {

	private final JwtEncoder jwtEncoder = mock(JwtEncoder.class);
	private final JwtService jwtService = new JwtService(jwtEncoder, 900);

	@Test
	void accessTokenContainsCurrentAuthenticationVersion() {
		UserAccount user = mock(UserAccount.class);
		when(user.getId()).thenReturn(1L);
		when(user.getEmail()).thenReturn("user@example.com");
		when(user.getUsername()).thenReturn("member");
		when(user.getRole()).thenReturn(com.fpt.ibom.auth.entity.UserRole.MEMBER);
		when(user.getAuthVersion()).thenReturn(3L);
		when(jwtEncoder.encode(any())).thenReturn(Jwt.withTokenValue("access-token").header("alg", "HS256")
				.claim("sub", "1").build());

		assertEquals("access-token", jwtService.createAccessToken(user));

		ArgumentCaptor<JwtEncoderParameters> parameters = ArgumentCaptor.forClass(JwtEncoderParameters.class);
		verify(jwtEncoder).encode(parameters.capture());
		JwtClaimsSet claims = parameters.getValue().getClaims();
		assertEquals(3L, ((Number) claims.getClaim("auth_version")).longValue());
	}
}
