package com.fpt.ibom.master.controller;

import java.util.List;

import com.fpt.ibom.common.ApiResponse;
import com.fpt.ibom.master.dto.SkillCategoryResponse;
import com.fpt.ibom.master.service.SkillCategoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/master/skill-categories")
public class SkillCategoryController {

	private final SkillCategoryService skillCategoryService;

	public SkillCategoryController(SkillCategoryService skillCategoryService) {
		this.skillCategoryService = skillCategoryService;
	}

	@GetMapping
	public ResponseEntity<ApiResponse<List<SkillCategoryResponse>>> list() {
		return ResponseEntity.ok(new ApiResponse<>(200, "Success", skillCategoryService.list()));
	}
}
