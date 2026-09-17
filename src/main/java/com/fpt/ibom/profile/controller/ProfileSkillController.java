package com.fpt.ibom.profile.controller;

import java.util.List;

import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.common.ApiResponse;
import com.fpt.ibom.profile.dto.ProfileSkillMutationResponse;
import com.fpt.ibom.profile.dto.ProfileSkillRequest;
import com.fpt.ibom.profile.dto.ProfileSkillResponse;
import com.fpt.ibom.profile.dto.ProfileVersionResponse;
import com.fpt.ibom.profile.service.ProfileSkillService;
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
@RequestMapping("/api/profiles/{profileId}/skills")
public class ProfileSkillController {
	private final ProfileSkillService profileSkillService;

	public ProfileSkillController(ProfileSkillService profileSkillService) {
		this.profileSkillService = profileSkillService;
	}

	@GetMapping
	public ResponseEntity<ApiResponse<List<ProfileSkillResponse>>> list(
			@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long profileId) {
		return ResponseEntity.ok(new ApiResponse<>(200, "Success", profileSkillService.list(principal, profileId)));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<ProfileSkillMutationResponse>> create(
			@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long profileId,
			@Valid @RequestBody ProfileSkillRequest request) {
		return ResponseEntity.status(201).body(new ApiResponse<>(201, "Created",
				profileSkillService.create(principal, profileId, request)));
	}

	@PutMapping("/{profileSkillId}")
	public ResponseEntity<ApiResponse<ProfileSkillMutationResponse>> update(
			@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long profileId,
			@PathVariable Long profileSkillId, @Valid @RequestBody ProfileSkillRequest request) {
		return ResponseEntity.ok(new ApiResponse<>(200, "Success",
				profileSkillService.update(principal, profileId, profileSkillId, request)));
	}

	@DeleteMapping("/{profileSkillId}")
	public ResponseEntity<ApiResponse<ProfileVersionResponse>> delete(
			@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long profileId,
			@PathVariable Long profileSkillId, @Valid @RequestBody ProfileVersionResponse request) {
		return ResponseEntity.ok(new ApiResponse<>(200, "Success",
				profileSkillService.delete(principal, profileId, profileSkillId, request.profileVersion())));
	}
}
