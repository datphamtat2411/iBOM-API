package com.fpt.ibom.profile.controller;

import java.util.List;

import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.common.ApiResponse;
import com.fpt.ibom.profile.dto.EducationMutationResponse;
import com.fpt.ibom.profile.dto.EducationRequest;
import com.fpt.ibom.profile.dto.EducationResponse;
import com.fpt.ibom.profile.dto.ProfileVersionResponse;
import com.fpt.ibom.profile.service.EducationService;
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
@RequestMapping("/api/profiles/{profileId}/educations")
public class EducationController {
	private final EducationService educationService;

	public EducationController(EducationService educationService) {
		this.educationService = educationService;
	}

	@GetMapping
	public ResponseEntity<ApiResponse<List<EducationResponse>>> list(@AuthenticationPrincipal UserPrincipal principal,
			@PathVariable Long profileId) {
		return ResponseEntity.ok(new ApiResponse<>(200, "Success", educationService.list(principal.userId(), profileId)));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<EducationMutationResponse>> create(@AuthenticationPrincipal UserPrincipal principal,
			@PathVariable Long profileId, @Valid @RequestBody EducationRequest request) {
		return ResponseEntity.status(201).body(new ApiResponse<>(201, "Created",
				educationService.create(principal.userId(), profileId, request)));
	}

	@PutMapping("/{educationId}")
	public ResponseEntity<ApiResponse<EducationMutationResponse>> update(
			@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long profileId,
			@PathVariable Long educationId, @Valid @RequestBody EducationRequest request) {
		return ResponseEntity.ok(new ApiResponse<>(200, "Success",
				educationService.update(principal.userId(), profileId, educationId, request)));
	}

	@DeleteMapping("/{educationId}")
	public ResponseEntity<ApiResponse<ProfileVersionResponse>> delete(
			@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long profileId,
			@PathVariable Long educationId, @Valid @RequestBody ProfileVersionResponse request) {
		return ResponseEntity.ok(new ApiResponse<>(200, "Success",
				educationService.delete(principal.userId(), profileId, educationId, request.profileVersion())));
	}
}
