package com.fpt.ibom.profile.controller;

import java.util.List;

import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.common.ApiResponse;
import com.fpt.ibom.profile.dto.CertificateMutationResponse;
import com.fpt.ibom.profile.dto.CertificateRequest;
import com.fpt.ibom.profile.dto.CertificateResponse;
import com.fpt.ibom.profile.dto.ProfileVersionResponse;
import com.fpt.ibom.profile.service.CertificateService;
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
@RequestMapping("/api/profiles/{profileId}/certificates")
public class CertificateController {
	private final CertificateService certificateService;

	public CertificateController(CertificateService certificateService) {
		this.certificateService = certificateService;
	}

	@GetMapping
	public ResponseEntity<ApiResponse<List<CertificateResponse>>> list(
			@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long profileId) {
		return ResponseEntity.ok(new ApiResponse<>(200, "Success", certificateService.list(principal.userId(), profileId)));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<CertificateMutationResponse>> create(
			@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long profileId,
			@Valid @RequestBody CertificateRequest request) {
		return ResponseEntity.status(201).body(new ApiResponse<>(201, "Created",
				certificateService.create(principal.userId(), profileId, request)));
	}

	@PutMapping("/{certificateId}")
	public ResponseEntity<ApiResponse<CertificateMutationResponse>> update(
			@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long profileId,
			@PathVariable Long certificateId, @Valid @RequestBody CertificateRequest request) {
		return ResponseEntity.ok(new ApiResponse<>(200, "Success",
				certificateService.update(principal.userId(), profileId, certificateId, request)));
	}

	@DeleteMapping("/{certificateId}")
	public ResponseEntity<ApiResponse<ProfileVersionResponse>> delete(
			@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long profileId,
			@PathVariable Long certificateId, @Valid @RequestBody ProfileVersionResponse request) {
		return ResponseEntity.ok(new ApiResponse<>(200, "Success",
				certificateService.delete(principal.userId(), profileId, certificateId, request.profileVersion())));
	}
}
