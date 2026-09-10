package com.fpt.ibom.profile.controller;

import java.util.List;

import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.common.ApiResponse;
import com.fpt.ibom.profile.dto.ProfileLanguageMutationResponse;
import com.fpt.ibom.profile.dto.ProfileLanguageRequest;
import com.fpt.ibom.profile.dto.ProfileLanguageResponse;
import com.fpt.ibom.profile.dto.ProfileVersionResponse;
import com.fpt.ibom.profile.service.ProfileLanguageService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profiles/{profileId}/languages")
public class ProfileLanguageController {
	private final ProfileLanguageService profileLanguageService;

	public ProfileLanguageController(ProfileLanguageService profileLanguageService) {
		this.profileLanguageService = profileLanguageService;
	}

	@GetMapping
	public ResponseEntity<ApiResponse<List<ProfileLanguageResponse>>> list(
			@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long profileId) {
		return ResponseEntity.ok(new ApiResponse<>(200, "Success",
				profileLanguageService.list(principal.userId(), profileId)));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<ProfileLanguageMutationResponse>> create(
			@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long profileId,
			@Valid @RequestBody ProfileLanguageRequest request) {
		return ResponseEntity.status(201).body(new ApiResponse<>(201, "Created",
				profileLanguageService.create(principal.userId(), profileId, request)));
	}

	@PutMapping("/{profileLanguageId}")
	public ResponseEntity<ApiResponse<ProfileLanguageMutationResponse>> update(
			@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long profileId,
			@PathVariable Long profileLanguageId, @Valid @RequestBody ProfileLanguageRequest request) {
		return ResponseEntity.ok(new ApiResponse<>(200,
				"Success", profileLanguageService.update(principal.userId(), profileId, profileLanguageId, request)));
	}

	@DeleteMapping("/{profileLanguageId}")
	public ResponseEntity<ApiResponse<ProfileVersionResponse>> delete(
			@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long profileId,
			@PathVariable Long profileLanguageId, @Valid @RequestBody ProfileVersionResponse request) {
		return ResponseEntity.ok(new ApiResponse<>(200, "Success", profileLanguageService.delete(principal.userId(), profileId,
				profileLanguageId, request.profileVersion())));
	}
}
