package com.fpt.ibom.master.controller;

import com.fpt.ibom.common.ApiResponse;
import com.fpt.ibom.common.PageResponse;
import com.fpt.ibom.master.dto.SkillResponse;
import com.fpt.ibom.master.service.SkillService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
			@RequestParam(required = false) String search) {
		return ResponseEntity.ok(new ApiResponse<>(200, "Success", skillService.list(page, size, search)));
	}
}
