package com.fpt.ibom.profile.controller;

import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.common.ApiResponse;
import com.fpt.ibom.profile.dto.ProfileCompletenessResponse;
import com.fpt.ibom.profile.dto.ProfileCopyRequest;
import com.fpt.ibom.profile.dto.ProfileRequest;
import com.fpt.ibom.profile.dto.ProfileResponse;
import com.fpt.ibom.profile.dto.ProfileUpdateRequest;
import com.fpt.ibom.profile.dto.ProfileDetailResponse;
import com.fpt.ibom.profile.dto.ProfileSummaryResponse;
import com.fpt.ibom.profile.service.ProfileCompletenessService;
import com.fpt.ibom.profile.service.ProfileCopyService;
import com.fpt.ibom.profile.service.ProfileService;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profiles")
public class ProfileController {
	private final ProfileService profileService;
	private final ProfileCompletenessService profileCompletenessService;
	private final ProfileCopyService profileCopyService;

	public ProfileController(ProfileService profileService, ProfileCompletenessService profileCompletenessService,
			ProfileCopyService profileCopyService) {
		this.profileService = profileService;
		this.profileCompletenessService = profileCompletenessService;
		this.profileCopyService = profileCopyService;
	}

	@PostMapping
	public ResponseEntity<ApiResponse<ProfileResponse>> create(@AuthenticationPrincipal UserPrincipal principal,
			@Valid @RequestBody ProfileRequest request) {
		return ResponseEntity.status(201).body(new ApiResponse<>(201, "Created",
				profileService.create(principal.userId(), request)));
	}

	@PostMapping("/{sourceProfileId}/copy")
	public ResponseEntity<ApiResponse<ProfileResponse>> copy(@AuthenticationPrincipal UserPrincipal principal,
			@PathVariable Long sourceProfileId, @Valid @RequestBody ProfileCopyRequest request) {
		return ResponseEntity.status(201).body(new ApiResponse<>(201, "Created",
				profileCopyService.copy(principal.userId(), sourceProfileId, request)));
	}

	@GetMapping("/me")
	public ResponseEntity<ApiResponse<List<ProfileSummaryResponse>>> list(
			@AuthenticationPrincipal UserPrincipal principal) {
		return ResponseEntity.ok(new ApiResponse<>(200, "Success", profileService.list(principal.userId())));
	}

	@GetMapping("/{profileId}")
	public ResponseEntity<ApiResponse<ProfileDetailResponse>> get(@AuthenticationPrincipal UserPrincipal principal,
			@PathVariable Long profileId) {
		return ResponseEntity.ok(new ApiResponse<>(200, "Success", profileService.get(principal, profileId)));
	}

	@GetMapping("/{profileId}/completeness")
	public ResponseEntity<ApiResponse<ProfileCompletenessResponse>> completeness(
			@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long profileId) {
		return ResponseEntity.ok(new ApiResponse<>(200, "Success",
				profileCompletenessService.get(principal.userId(), profileId)));
	}

	@PutMapping("/{profileId}")
	public ResponseEntity<ApiResponse<ProfileDetailResponse>> update(@AuthenticationPrincipal UserPrincipal principal,
			@PathVariable Long profileId, @Valid @RequestBody ProfileUpdateRequest request) {
		return ResponseEntity.ok(new ApiResponse<>(200, "Success",
				profileService.update(principal, profileId, request)));
	}

	@DeleteMapping("/{profileId}")
	public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal UserPrincipal principal,
			@PathVariable Long profileId) {
		profileService.delete(principal, profileId);
		return ResponseEntity.ok(new ApiResponse<>(200, "Success", null));
	}
}
