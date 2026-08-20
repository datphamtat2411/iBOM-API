package com.fpt.ibom.auth;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;

import jakarta.servlet.http.Cookie;
import com.fpt.ibom.auth.controller.AuthController;
import com.fpt.ibom.auth.dto.AuthenticatedUser;
import com.fpt.ibom.auth.dto.LoginRequest;
import com.fpt.ibom.auth.dto.RegistrationCodeRequest;
import com.fpt.ibom.auth.dto.RegistrationRequest;
import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.security.UserAccountJwtAuthenticationConverter;
import com.fpt.ibom.auth.service.LoginService;
import com.fpt.ibom.auth.service.RegistrationService;
import com.fpt.ibom.auth.service.PasswordResetService;
import com.fpt.ibom.config.SecurityConfig;
import com.fpt.ibom.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = AuthControllerTest.ProtectedController.class)
@Import({AuthController.class, SecurityConfig.class, UserAccountJwtAuthenticationConverter.class, AuthControllerTest.ProtectedController.class})
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private LoginService loginService;

	@MockitoBean
	private RegistrationService registrationService;

	@MockitoBean
	private PasswordResetService passwordResetService;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@MockitoBean
	private UserAccountRepository userAccountRepository;

	@Test
	void logsInAndSetsHttpOnlyRefreshCookie() throws Exception {
		when(loginService.authenticate(new LoginRequest("user@example.com", "correct-password")))
				.thenReturn(new LoginService.AuthenticationResult("access-token", "refresh-token", 604800,
						new AuthenticatedUser(1L, "user@example.com", "member", UserRole.MEMBER)));

		mockMvc.perform(post("/api/auth/login")
					.contentType("application/json")
					.content("{\"email\":\"user@example.com\",\"password\":\"correct-password\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(200))
				.andExpect(jsonPath("$.data.accessToken").value("access-token"))
				.andExpect(jsonPath("$.data.user.id").value(1))
				.andExpect(jsonPath("$.data.user.email").value("user@example.com"))
				.andExpect(jsonPath("$.data.user.username").value("member"))
				.andExpect(jsonPath("$.data.user.role").value("MEMBER"))
				.andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.allOf(
						org.hamcrest.Matchers.containsString("refresh_token=refresh-token"),
						org.hamcrest.Matchers.containsString("HttpOnly"),
						org.hamcrest.Matchers.containsString("Secure"))));
	}

	@Test
	void loginAlsoSetsClientReadableCsrfCookie() throws Exception {
		when(loginService.authenticate(new LoginRequest("user@example.com", "correct-password")))
				.thenReturn(authenticationResult());

		mockMvc.perform(post("/api/auth/login").contentType("application/json")
					.content("{\"email\":\"user@example.com\",\"password\":\"correct-password\"}"))
				.andExpect(status().isOk())
				.andExpect(header().stringValues("Set-Cookie", org.hamcrest.Matchers.hasItem(
						org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsString("XSRF-TOKEN="),
								org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("HttpOnly")),
								org.hamcrest.Matchers.containsString("Secure")))));
	}

	@Test
	void requiresCsrfHeaderForRefresh() throws Exception {
		mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", "refresh-token")))
				.andExpect(status().isForbidden());
	}

	@Test
	void refreshRotatesCookiesWithValidCsrfState() throws Exception {
		when(loginService.refresh("refresh-token")).thenReturn(authenticationResult());

		mockMvc.perform(post("/api/auth/refresh")
					.cookie(new Cookie("refresh_token", "refresh-token"), new Cookie("XSRF-TOKEN", "csrf-token"))
					.header("X-XSRF-TOKEN", "csrf-token"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accessToken").value("access-token"))
				.andExpect(header().stringValues("Set-Cookie", org.hamcrest.Matchers.hasItem(
						org.hamcrest.Matchers.containsString("XSRF-TOKEN="))));
	}

	@Test
	void logoutIsCsrfProtectedAndClearsBothCookies() throws Exception {
		mockMvc.perform(post("/api/auth/logout")
					.cookie(new Cookie("XSRF-TOKEN", "csrf-token"))
					.header("X-XSRF-TOKEN", "csrf-token"))
				.andExpect(status().isOk())
				.andExpect(header().stringValues("Set-Cookie", org.hamcrest.Matchers.hasItems(
						org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsString("refresh_token="),
								org.hamcrest.Matchers.containsString("Max-Age=0")),
						org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsString("XSRF-TOKEN="),
								org.hamcrest.Matchers.containsString("Max-Age=0")))));
		verify(loginService).logout(null);
	}

	@Test
	void returnsUnauthorizedForInvalidCredentials() throws Exception {
		when(loginService.authenticate(new LoginRequest("user@example.com", "wrong-password")))
				.thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

		mockMvc.perform(post("/api/auth/login")
					.contentType("application/json")
					.content("{\"email\":\"user@example.com\",\"password\":\"wrong-password\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Invalid credentials"));
	}

	@Test
	void returnsForbiddenForInactiveAccount() throws Exception {
		when(loginService.authenticate(new LoginRequest("user@example.com", "correct-password")))
				.thenThrow(new ApiException(HttpStatus.FORBIDDEN, "Account is inactive"));

		mockMvc.perform(post("/api/auth/login")
					.contentType("application/json")
					.content("{\"email\":\"user@example.com\",\"password\":\"correct-password\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("Account is inactive"));
	}

	@Test
	void permitsRegistrationEndpointsWithoutAuthentication() throws Exception {
		mockMvc.perform(post("/api/auth/registration-code")
					.contentType("application/json")
					.content("{\"email\":\"user@gmail.com\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").doesNotExist());

		mockMvc.perform(post("/api/auth/register")
					.contentType("application/json")
					.content("{\"email\":\"user@gmail.com\",\"username\":\"member\",\"password\":\"password1\",\"verificationCode\":\"123456\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accessToken").doesNotExist())
				.andExpect(header().doesNotExist("Set-Cookie"));
	}

	@Test
	void validatesRegistrationCodeRequest() throws Exception {
		mockMvc.perform(post("/api/auth/registration-code")
					.contentType("application/json")
					.content("{\"email\":\"not-an-email\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsRegistrationPasswordOutsidePolicy() throws Exception {
		mockMvc.perform(post("/api/auth/register")
					.contentType("application/json")
					.content("{\"email\":\"user@gmail.com\",\"username\":\"member\",\"password\":\"short\",\"verificationCode\":\"123456\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void permitsPasswordResetEndpointsWithoutCsrfOrCookies() throws Exception {
		mockMvc.perform(post("/api/auth/password-reset-code")
					.contentType("application/json")
					.content("{\"email\":\"user@example.com\"}"))
				.andExpect(status().isOk())
				.andExpect(header().doesNotExist("Set-Cookie"));

		mockMvc.perform(post("/api/auth/password-reset-code/verify")
					.contentType("application/json")
					.content("{\"email\":\"user@example.com\",\"verificationCode\":\"123456\"}"))
				.andExpect(status().isOk())
				.andExpect(header().doesNotExist("Set-Cookie"));

		mockMvc.perform(post("/api/auth/password-reset")
					.contentType("application/json")
					.content("{\"email\":\"user@example.com\",\"verificationCode\":\"123456\",\"password\":\"password1\"}"))
				.andExpect(status().isOk())
				.andExpect(header().doesNotExist("Set-Cookie"));
	}

	@Test
	void validatesPasswordResetRequestShapeAndPolicy() throws Exception {
		mockMvc.perform(post("/api/auth/password-reset-code/verify")
					.contentType("application/json")
					.content("{\"email\":\"user@example.com\",\"verificationCode\":\"invalid\"}"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/auth/password-reset")
					.contentType("application/json")
					.content("{\"email\":\"user@example.com\",\"verificationCode\":\"123456\",\"password\":\"short\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void permitsProtectedAccessWithValidJwt() throws Exception {
		when(userAccountRepository.findById(1L)).thenReturn(Optional.of(new UserAccount("user@example.com", "member",
				"hash", UserRole.MEMBER, UserStatus.ACTIVE)));
		when(jwtDecoder.decode("valid-token")).thenReturn(accessToken("valid-token", UserRole.MEMBER));

		mockMvc.perform(get("/test/protected").header("Authorization", "Bearer valid-token"))
				.andExpect(status().isOk());
	}

	@Test
	void rejectsExistingAccessTokenForInactiveAccount() throws Exception {
		when(userAccountRepository.findById(1L)).thenReturn(Optional.of(new UserAccount("user@example.com", "member",
				"hash", UserRole.MEMBER, UserStatus.INACTIVE)));
		when(jwtDecoder.decode("inactive-token")).thenReturn(accessToken("inactive-token", UserRole.MEMBER));

		mockMvc.perform(get("/test/protected").header("Authorization", "Bearer inactive-token"))
				.andExpect(status().isUnauthorized());
	}

	private Jwt accessToken(String token, UserRole role) {
		Instant now = Instant.now();
		return Jwt.withTokenValue(token)
				.header("alg", "HS256")
				.subject("1")
				.issuedAt(now)
				.expiresAt(now.plusSeconds(900))
				.claim("email", "user@example.com")
				.claim("username", "member")
				.claim("role", role.name())
				.build();
	}

	private LoginService.AuthenticationResult authenticationResult() {
		return new LoginService.AuthenticationResult("access-token", "refresh-token", 604800,
				new AuthenticatedUser(1L, "user@example.com", "member", UserRole.MEMBER));
	}

	@RestController
	static class ProtectedController {

		@GetMapping("/test/protected")
		void protectedEndpoint() {
		}
	}
}
