package com.fpt.ibom.member.controller;

import java.util.List;

import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.common.ApiResponse;
import com.fpt.ibom.common.PageResponse;
import com.fpt.ibom.member.dto.MemberLanguageSearchResponse;
import com.fpt.ibom.member.service.MemberLanguageSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
public class MemberLanguageSearchController {

	private final MemberLanguageSearchService service;

	public MemberLanguageSearchController(MemberLanguageSearchService service) {
		this.service = service;
	}

	@GetMapping("/search-by-language")
	public ResponseEntity<ApiResponse<PageResponse<MemberLanguageSearchResponse>>> search(
			@RequestParam(required = false) List<Long> languageIds, @RequestParam(required = false) List<String> levels,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
			@RequestParam(required = false) String status) {
		UserStatus statusFilter = status == null ? null : parseStatus(status);
		return ResponseEntity.ok(new ApiResponse<>(200, "Success", service.search(languageIds, levels, page, size, statusFilter)));
	}

	private UserStatus parseStatus(String status) {
		try {
			return UserStatus.valueOf(status);
		} catch (IllegalArgumentException exception) {
			throw new com.fpt.ibom.exception.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
					com.fpt.ibom.exception.ErrorCode.VALIDATION_ERROR, "Validation failed");
		}
	}
}
