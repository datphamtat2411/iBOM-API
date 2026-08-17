package com.fpt.ibom.auth.security;

import java.util.List;

import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

@Component
public class UserAccountJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	private final UserAccountRepository userAccountRepository;

	public UserAccountJwtAuthenticationConverter(UserAccountRepository userAccountRepository) {
		this.userAccountRepository = userAccountRepository;
	}

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		try {
			UserPrincipal principal = new UserPrincipal(Long.valueOf(jwt.getSubject()), jwt.getClaimAsString("email"),
					jwt.getClaimAsString("username"), UserRole.valueOf(jwt.getClaimAsString("role")));
			boolean active = userAccountRepository.findById(principal.userId())
					.map(user -> user.getStatus() == UserStatus.ACTIVE)
					.orElse(false);
			if (!active) {
				throw new InvalidBearerTokenException("Account is inactive");
			}
			return new UsernamePasswordAuthenticationToken(principal, jwt,
					List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
		} catch (InvalidBearerTokenException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new InvalidBearerTokenException("Invalid access token", exception);
		}
	}
}
