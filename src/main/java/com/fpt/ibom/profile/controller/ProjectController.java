package com.fpt.ibom.profile.controller;

import java.util.List;

import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.common.ApiResponse;
import com.fpt.ibom.profile.dto.ProjectMutationResponse;
import com.fpt.ibom.profile.dto.ProjectRequest;
import com.fpt.ibom.profile.dto.ProjectResponse;
import com.fpt.ibom.profile.dto.ProfileVersionResponse;
import com.fpt.ibom.profile.service.ProjectService;
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
@RequestMapping("/api/profiles/{profileId}/projects")
public class ProjectController {
	private final ProjectService projectService;

	public ProjectController(ProjectService projectService) {
		this.projectService = projectService;
	}

	@GetMapping
	public ResponseEntity<ApiResponse<List<ProjectResponse>>> list(@AuthenticationPrincipal UserPrincipal principal,
			@PathVariable Long profileId) {
		return ResponseEntity.ok(new ApiResponse<>(200, "Success", projectService.list(principal, profileId)));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<ProjectMutationResponse>> create(@AuthenticationPrincipal UserPrincipal principal,
			@PathVariable Long profileId, @Valid @RequestBody ProjectRequest request) {
		return ResponseEntity.status(201).body(new ApiResponse<>(201, "Created",
				projectService.create(principal, profileId, request)));
	}

	@PutMapping("/{projectId}")
	public ResponseEntity<ApiResponse<ProjectMutationResponse>> update(@AuthenticationPrincipal UserPrincipal principal,
			@PathVariable Long profileId, @PathVariable Long projectId, @Valid @RequestBody ProjectRequest request) {
		return ResponseEntity.ok(new ApiResponse<>(200, "Success",
				projectService.update(principal, profileId, projectId, request)));
	}

	@DeleteMapping("/{projectId}")
	public ResponseEntity<ApiResponse<ProfileVersionResponse>> delete(
			@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long profileId,
			@PathVariable Long projectId, @Valid @RequestBody ProfileVersionResponse request) {
		return ResponseEntity.ok(new ApiResponse<>(200, "Success",
				projectService.delete(principal, profileId, projectId, request.profileVersion())));
	}
}
