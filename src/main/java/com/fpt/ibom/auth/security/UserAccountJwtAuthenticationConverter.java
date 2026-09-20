package com.fpt.ibom.auth.security;

import java.util.List;

import com.fpt.ibom.auth.entity.UserAccount;
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
			long tokenAuthenticationVersion = authenticationVersion(jwt);
			UserAccount user = userAccountRepository.findById(principal.userId()).orElse(null);
			if (user == null || user.getStatus() != UserStatus.ACTIVE) {
				throw new InvalidBearerTokenException("Account is inactive");
			}
			if (user.getAuthVersion() != tokenAuthenticationVersion) {
				throw new InvalidBearerTokenException("Authentication state is outdated");
			}
			return new UsernamePasswordAuthenticationToken(principal, jwt,
					List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
		} catch (InvalidBearerTokenException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new InvalidBearerTokenException("Invalid access token", exception);
		}
	}

	private long authenticationVersion(Jwt jwt) {
		Object claim = jwt.getClaim(JwtService.AUTHENTICATION_VERSION_CLAIM);
		return claim == null ? 0L : Long.parseLong(claim.toString());
	}
}
