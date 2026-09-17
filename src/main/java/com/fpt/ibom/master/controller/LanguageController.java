package com.fpt.ibom.master.controller;

import com.fpt.ibom.common.ApiResponse;
import com.fpt.ibom.common.PageResponse;
import com.fpt.ibom.master.dto.LanguageResponse;
import com.fpt.ibom.master.dto.LanguageRequest;
import com.fpt.ibom.master.service.LanguageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

	@PostMapping
	public ResponseEntity<ApiResponse<LanguageResponse>> create(@Valid @RequestBody LanguageRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(new ApiResponse<>(HttpStatus.CREATED.value(), "Success", languageService.create(request)));
	}

	@PutMapping("/{languageId}")
	public ResponseEntity<ApiResponse<LanguageResponse>> update(@PathVariable Long languageId,
			@Valid @RequestBody LanguageRequest request) {
		return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Success",
				languageService.update(languageId, request)));
	}

	@DeleteMapping("/{languageId}")
	public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long languageId) {
		languageService.delete(languageId);
		return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Success", null));
	}
}
