package com.fpt.ibom.user.controller;

import java.util.List;

import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.common.ApiResponse;
import com.fpt.ibom.common.PageResponse;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.user.dto.UserSummaryResponse;
import com.fpt.ibom.user.dto.ManagedUserCreateRequest;
import com.fpt.ibom.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@PostMapping
	public ResponseEntity<ApiResponse<UserSummaryResponse>> create(@Valid @RequestBody ManagedUserCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(new ApiResponse<>(201, "Created", userService.create(request)));
	}

	@GetMapping
	public ResponseEntity<ApiResponse<PageResponse<UserSummaryResponse>>> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size,
			@RequestParam(required = false) String search,
			@RequestParam(name = "role", required = false) List<String> roles) {
		List<UserRole> roleFilters = roles == null ? null : roles.stream().map(this::parseRole).toList();
		return ResponseEntity.ok(new ApiResponse<>(200, "Success", userService.list(page, size, search, roleFilters)));
	}

	private UserRole parseRole(String role) {
		try {
			return UserRole.valueOf(role);
		} catch (IllegalArgumentException exception) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Validation failed");
		}
	}
}
