package com.fpt.ibom.master.controller;

import com.fpt.ibom.common.ApiResponse;
import com.fpt.ibom.common.PageResponse;
import com.fpt.ibom.master.dto.SkillRequest;
import com.fpt.ibom.master.dto.SkillResponse;
import com.fpt.ibom.master.service.SkillService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/master/skills")
public class SkillController {

	private final SkillService skillService;

	public SkillController(SkillService skillService) {
		this.skillService = skillService;
	}

	@GetMapping
	public ResponseEntity<ApiResponse<PageResponse<SkillResponse>>> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size,
			@RequestParam(required = false) String search,
			@RequestParam(required = false) Long categoryId) {
		return ResponseEntity.ok(new ApiResponse<>(200, "Success", skillService.list(page, size, search, categoryId)));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<SkillResponse>> create(@Valid @RequestBody SkillRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(new ApiResponse<>(201, "Created", skillService.create(request)));
	}

	@PutMapping("/{skillId}")
	public ResponseEntity<ApiResponse<SkillResponse>> update(@PathVariable Long skillId,
			@Valid @RequestBody SkillRequest request) {
		return ResponseEntity.ok(new ApiResponse<>(200, "Success", skillService.update(skillId, request)));
	}

	@DeleteMapping("/{skillId}")
	public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long skillId) {
		skillService.delete(skillId);
		return ResponseEntity.ok(new ApiResponse<>(200, "Success", null));
	}
}
