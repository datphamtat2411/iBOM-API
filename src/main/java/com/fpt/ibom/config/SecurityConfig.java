package com.fpt.ibom.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fpt.ibom.auth.security.UserAccountJwtAuthenticationConverter;
import com.fpt.ibom.common.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			ObjectMapper objectMapper,
			UserAccountJwtAuthenticationConverter jwtAuthenticationConverter
	) throws Exception {

		CsrfTokenRequestAttributeHandler csrfRequestHandler =
				new CsrfTokenRequestAttributeHandler();

		return http
				.csrf(csrf -> csrf
						.csrfTokenRepository(csrfTokenRepository())
						.csrfTokenRequestHandler(csrfRequestHandler)
						.requireCsrfProtectionMatcher(
								new OrRequestMatcher(
										new AntPathRequestMatcher("/api/auth/refresh", "POST"),
										new AntPathRequestMatcher("/api/auth/logout", "POST")
								)
						)
				)
				.sessionManagement(session ->
						session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.oauth2ResourceServer(oauth2 ->
						oauth2.jwt(jwt ->
								jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(
								"/health",
								"/v3/api-docs/**",
								"/swagger-ui/**",
								"/swagger-ui.html",
								"/api/auth/login",
								"/api/auth/registration-code",
								"/api/auth/register",
								"/api/auth/password-reset-code",
								"/api/auth/password-reset-code/verify",
								"/api/auth/password-reset",
								"/api/auth/refresh",
								"/api/auth/logout"
						).permitAll()
						.anyRequest().authenticated()
				)
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint((request, response, exception) ->
								writeError(response, objectMapper, HttpStatus.UNAUTHORIZED))
						.accessDeniedHandler((request, response, exception) ->
								writeError(response, objectMapper, HttpStatus.FORBIDDEN))
				)
				.build();
	}

	private CookieCsrfTokenRepository csrfTokenRepository() {
		CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		repository.setCookieName("XSRF-TOKEN");
		repository.setHeaderName("X-XSRF-TOKEN");
		repository.setCookieCustomizer(cookie -> cookie.path("/").secure(true).sameSite("Strict"));
		return repository;
	}

	@Bean
	JwtEncoder jwtEncoder(@Value("${app.auth.jwt.secret}") String secret) {
		OctetSequenceKey key = new OctetSequenceKey.Builder(signingKey(secret).getEncoded())
				.algorithm(JWSAlgorithm.HS256)
				.build();
		return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(key)));
	}

	@Bean
	JwtDecoder jwtDecoder(@Value("${app.auth.jwt.secret}") String secret) {
		return NimbusJwtDecoder.withSecretKey(signingKey(secret)).macAlgorithm(MacAlgorithm.HS256).build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(12);
	}

	private SecretKey signingKey(String secret) {
		return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	private void writeError(HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(), new ApiResponse<>(status.value(), status.getReasonPhrase(), null));
	}
}
