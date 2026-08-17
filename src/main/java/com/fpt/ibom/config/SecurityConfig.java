package com.fpt.ibom.config;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fpt.ibom.common.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
		return http
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers("/health", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint((request, response, exception) ->
								writeError(response, objectMapper, HttpStatus.UNAUTHORIZED))
						.accessDeniedHandler((request, response, exception) ->
								writeError(response, objectMapper, HttpStatus.FORBIDDEN)))
				.build();
	}

	private void writeError(HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(), new ApiResponse<>(status.value(), status.getReasonPhrase(), null));
	}
}
