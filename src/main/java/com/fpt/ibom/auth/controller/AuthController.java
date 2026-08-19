package com.fpt.ibom.auth.controller;

import com.fpt.ibom.auth.dto.LoginRequest;
import com.fpt.ibom.auth.dto.LoginResponse;
import com.fpt.ibom.auth.dto.RegistrationCodeRequest;
import com.fpt.ibom.auth.dto.RegistrationRequest;
import com.fpt.ibom.auth.service.LoginService;
import com.fpt.ibom.auth.service.LoginService.AuthenticationResult;
import com.fpt.ibom.auth.service.RegistrationService;
import com.fpt.ibom.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final LoginService loginService;
	private final RegistrationService registrationService;

	public AuthController(LoginService loginService, RegistrationService registrationService) {
		this.loginService = loginService;
		this.registrationService = registrationService;
	}

	@PostMapping("/login")
	public org.springframework.http.ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
		LoginService.AuthenticationResult result = loginService.authenticate(request);
		ResponseCookie cookie = ResponseCookie.from("refresh_token", result.refreshToken())
				.httpOnly(true)
				.secure(true)
				.path("/api/auth")
				.maxAge(result.refreshTokenTtlSeconds())
				.sameSite("Strict")
				.build();
		return org.springframework.http.ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, cookie.toString())
				.body(new ApiResponse<>(200, "Success", new LoginResponse(result.accessToken(), result.user())));
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
}
