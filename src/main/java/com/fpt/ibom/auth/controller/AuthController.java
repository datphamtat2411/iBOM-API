package com.fpt.ibom.auth.controller;

import java.security.SecureRandom;
import java.util.Base64;

import com.fpt.ibom.auth.dto.LoginRequest;
import com.fpt.ibom.auth.dto.LoginResponse;
import com.fpt.ibom.auth.dto.PasswordResetCodeRequest;
import com.fpt.ibom.auth.dto.PasswordResetCodeVerificationRequest;
import com.fpt.ibom.auth.dto.PasswordResetRequest;
import com.fpt.ibom.auth.dto.RegistrationCodeRequest;
import com.fpt.ibom.auth.dto.RegistrationRequest;
import com.fpt.ibom.auth.service.LoginService;
import com.fpt.ibom.auth.service.LoginService.AuthenticationResult;
import com.fpt.ibom.auth.service.RegistrationService;
import com.fpt.ibom.auth.service.PasswordResetService;
import com.fpt.ibom.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final LoginService loginService;
	private final RegistrationService registrationService;
	private final PasswordResetService passwordResetService;
	private final SecureRandom secureRandom = new SecureRandom();

	public AuthController(LoginService loginService, RegistrationService registrationService,
			PasswordResetService passwordResetService) {
		this.loginService = loginService;
		this.registrationService = registrationService;
		this.passwordResetService = passwordResetService;
	}

	@PostMapping("/login")
	public org.springframework.http.ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
		LoginService.AuthenticationResult result = loginService.authenticate(request);
		return authenticationResponse(result);
	}

	@PostMapping("/refresh")
	public org.springframework.http.ResponseEntity<ApiResponse<LoginResponse>> refresh(
			@CookieValue(value = "refresh_token", required = false) String refreshToken) {
		return authenticationResponse(loginService.refresh(refreshToken));
	}

	@PostMapping("/logout")
	public org.springframework.http.ResponseEntity<ApiResponse<Void>> logout(
			@CookieValue(value = "refresh_token", required = false) String refreshToken) {
		loginService.logout(refreshToken);
		return org.springframework.http.ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, refreshCookie("", 0).toString())
				.header(HttpHeaders.SET_COOKIE, csrfCookie("", 0).toString())
				.body(new ApiResponse<>(200, "Success", null));
	}

	private org.springframework.http.ResponseEntity<ApiResponse<LoginResponse>> authenticationResponse(AuthenticationResult result) {
		return org.springframework.http.ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken(), result.refreshTokenTtlSeconds()).toString())
				.header(HttpHeaders.SET_COOKIE, csrfCookie(generateCsrfToken(), result.refreshTokenTtlSeconds()).toString())
				.body(new ApiResponse<>(200, "Success", new LoginResponse(result.accessToken(), result.user())));
	}

	private ResponseCookie refreshCookie(String value, long maxAge) {
		return ResponseCookie.from("refresh_token", value).httpOnly(true).secure(true).path("/api/auth")
				.maxAge(maxAge).sameSite("Strict").build();
	}

	private ResponseCookie csrfCookie(String value, long maxAge) {
		return ResponseCookie.from("XSRF-TOKEN", value).httpOnly(false).secure(true).path("/api/auth")
				.maxAge(maxAge).sameSite("Strict").build();
	}

	private String generateCsrfToken() {
		byte[] bytes = new byte[32];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder()
				.withoutPadding()
				.encodeToString(bytes);
	}

	@PostMapping("/registration-code")
	public org.springframework.http.ResponseEntity<ApiResponse<Void>> requestRegistrationCode(
			@Valid @RequestBody RegistrationCodeRequest request) {
		registrationService.requestVerificationCode(request);
		return org.springframework.http.ResponseEntity.ok(new ApiResponse<>(200, "Success", null));
	}

	@PostMapping("/register")
	public org.springframework.http.ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegistrationRequest request) {
		registrationService.register(request);
		return org.springframework.http.ResponseEntity.ok(new ApiResponse<>(200, "Success", null));
	}

	@PostMapping("/password-reset-code")
	public org.springframework.http.ResponseEntity<ApiResponse<Void>> requestPasswordResetCode(
			@Valid @RequestBody PasswordResetCodeRequest request) {
		passwordResetService.requestCode(request);
		return org.springframework.http.ResponseEntity.ok(new ApiResponse<>(200, "Success", null));
	}

	@PostMapping("/password-reset-code/verify")
	public org.springframework.http.ResponseEntity<ApiResponse<Void>> verifyPasswordResetCode(
			@Valid @RequestBody PasswordResetCodeVerificationRequest request) {
		passwordResetService.verifyCode(request);
		return org.springframework.http.ResponseEntity.ok(new ApiResponse<>(200, "Success", null));
	}

	@PostMapping("/password-reset")
	public org.springframework.http.ResponseEntity<ApiResponse<Void>> resetPassword(
			@Valid @RequestBody PasswordResetRequest request) {
		passwordResetService.resetPassword(request);
		return org.springframework.http.ResponseEntity.ok(new ApiResponse<>(200, "Success", null));
	}
}
