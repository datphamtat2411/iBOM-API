package com.fpt.ibom.master.controller;

import java.util.List;

import com.fpt.ibom.common.ApiResponse;
import com.fpt.ibom.master.dto.SeniorityMutationRequest;
import com.fpt.ibom.master.dto.SeniorityResponse;
import com.fpt.ibom.master.service.SeniorityService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/master/seniority")
public class SeniorityController {

	private final SeniorityService seniorityService;

	public SeniorityController(SeniorityService seniorityService) {
		this.seniorityService = seniorityService;
	}

	@GetMapping
	public ResponseEntity<ApiResponse<List<SeniorityResponse>>> list() {
		return ResponseEntity.ok(new ApiResponse<>(200, "Success", seniorityService.list()));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<SeniorityResponse>> create(@Valid @RequestBody SeniorityMutationRequest request) {
		return ResponseEntity.status(201).body(new ApiResponse<>(201, "Created", seniorityService.create(request)));
	}

	@PutMapping("/{seniorityId}")
	public ResponseEntity<ApiResponse<SeniorityResponse>> update(@PathVariable Long seniorityId,
			@Valid @RequestBody SeniorityMutationRequest request) {
		return ResponseEntity.ok(new ApiResponse<>(200, "Success", seniorityService.update(seniorityId, request)));
	}

	@DeleteMapping("/{seniorityId}")
	public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long seniorityId) {
		seniorityService.delete(seniorityId);
		return ResponseEntity.ok(new ApiResponse<>(200, "Success", null));
	}
}
