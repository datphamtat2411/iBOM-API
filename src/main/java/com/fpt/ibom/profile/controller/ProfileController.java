package com.fpt.ibom.profile.controller;

import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.common.ApiResponse;
import com.fpt.ibom.profile.dto.ProfileRequest;
import com.fpt.ibom.profile.dto.ProfileResponse;
import com.fpt.ibom.profile.dto.ProfileDetailResponse;
import com.fpt.ibom.profile.dto.ProfileSummaryResponse;
import com.fpt.ibom.profile.service.ProfileService;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profiles")
public class ProfileController {
	private final ProfileService profileService;

	public ProfileController(ProfileService profileService) {
		this.profileService = profileService;
	}

	@PostMapping
	public ResponseEntity<ApiResponse<ProfileResponse>> create(@AuthenticationPrincipal UserPrincipal principal,
			@Valid @RequestBody ProfileRequest request) {
		return ResponseEntity.status(201).body(new ApiResponse<>(201, "Created",
				profileService.create(principal.userId(), request)));
	}

	@GetMapping("/me")
	public ResponseEntity<ApiResponse<List<ProfileSummaryResponse>>> list(
			@AuthenticationPrincipal UserPrincipal principal) {
		return ResponseEntity.ok(new ApiResponse<>(200, "Success", profileService.list(principal.userId())));
	}

	@GetMapping("/{profileId}")
	public ResponseEntity<ApiResponse<ProfileDetailResponse>> get(@AuthenticationPrincipal UserPrincipal principal,
			@PathVariable Long profileId) {
		return ResponseEntity.ok(new ApiResponse<>(200, "Success", profileService.get(principal.userId(), profileId)));
	}
}
