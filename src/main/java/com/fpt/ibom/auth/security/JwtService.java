package com.fpt.ibom.auth.security;

import java.time.Instant;

import com.fpt.ibom.auth.entity.UserAccount;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

	private final JwtEncoder jwtEncoder;
	private final long accessTokenTtlSeconds;

	public JwtService(JwtEncoder jwtEncoder,
			@Value("${app.auth.jwt.access-token-ttl-seconds}") long accessTokenTtlSeconds) {
		this.jwtEncoder = jwtEncoder;
		this.accessTokenTtlSeconds = accessTokenTtlSeconds;
	}

	public String createAccessToken(UserAccount user) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject(user.getId().toString())
				.issuedAt(now)
				.expiresAt(now.plusSeconds(accessTokenTtlSeconds))
				.claim("email", user.getEmail())
				.claim("username", user.getUsername())
				.claim("role", user.getRole().name())
				.build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}
}
