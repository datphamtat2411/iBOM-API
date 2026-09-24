package com.fpt.ibom.member.controller;

import java.util.ArrayList;
import java.util.List;

import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.common.ApiResponse;
import com.fpt.ibom.common.PageResponse;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.member.dto.MemberSearchRequest;
import com.fpt.ibom.member.dto.MemberSearchResponse;
import com.fpt.ibom.member.service.MemberService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
public class MemberLanguageSearchController {

	private final MemberService memberService;

	public MemberLanguageSearchController(MemberService memberService) {
		this.memberService = memberService;
	}

	@GetMapping("/search-by-language")
	public ResponseEntity<ApiResponse<PageResponse<MemberSearchResponse>>> search(
			@RequestParam(required = false) List<Long> languageIds, @RequestParam(required = false) List<String> levels,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
			@RequestParam(required = false) String status) {
		UserStatus statusFilter = status == null ? null : parseStatus(status);
		List<MemberSearchRequest.LanguageCondition> languages = languageConditions(languageIds, levels);
		MemberSearchRequest request = new MemberSearchRequest(null,
				statusFilter == null ? null : statusFilter.name(), null, languages, page, size);
		return ResponseEntity.ok(new ApiResponse<>(200, "Success", memberService.search(request)));
	}

	private List<MemberSearchRequest.LanguageCondition> languageConditions(List<Long> languageIds,
			List<String> levels) {
		if (languageIds == null || languageIds.isEmpty()
				|| (levels != null && languageIds.size() != levels.size())) {
			throw validationError();
		}
		List<MemberSearchRequest.LanguageCondition> conditions = new ArrayList<>();
		for (int index = 0; index < languageIds.size(); index++) {
			conditions.add(new MemberSearchRequest.LanguageCondition(languageIds.get(index),
					levels == null ? null : levels.get(index)));
		}
		return conditions;
	}

	private UserStatus parseStatus(String status) {
		try {
			return UserStatus.valueOf(status);
		} catch (IllegalArgumentException exception) {
			throw validationError();
		}
	}

	private ApiException validationError() {
		return new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
				"Validation failed");
	}
}
