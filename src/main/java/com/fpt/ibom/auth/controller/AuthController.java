package com.fpt.ibom.auth.controller;

import com.fpt.ibom.auth.dto.LoginRequest;
import com.fpt.ibom.auth.dto.LoginResponse;
import com.fpt.ibom.auth.service.LoginService;
import com.fpt.ibom.auth.service.LoginService.AuthenticationResult;
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

	public AuthController(LoginService loginService) {
		this.loginService = loginService;
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
}
