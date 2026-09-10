package com.fpt.ibom.master.controller;

import com.fpt.ibom.common.ApiResponse;
import com.fpt.ibom.common.PageResponse;
import com.fpt.ibom.master.dto.LanguageResponse;
import com.fpt.ibom.master.service.LanguageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/master/languages")
public class LanguageController {

	private final LanguageService languageService;

	public LanguageController(LanguageService languageService) {
		this.languageService = languageService;
	}

	@GetMapping
	public ResponseEntity<ApiResponse<PageResponse<LanguageResponse>>> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size,
			@RequestParam(required = false) String search) {
		return ResponseEntity.ok(new ApiResponse<>(200, "Success", languageService.list(page, size, search)));
	}
}
