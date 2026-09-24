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
import com.fpt.ibom.member.dto.MemberSummaryResponse;
import com.fpt.ibom.member.service.MemberService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
public class MemberController {

	private final MemberService memberService;

	public MemberController(MemberService memberService) {
		this.memberService = memberService;
	}

	@GetMapping
	public ResponseEntity<ApiResponse<PageResponse<MemberSummaryResponse>>> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size,
			@RequestParam(required = false) String search,
			@RequestParam(required = false) String status) {
		UserStatus statusFilter = status == null ? null : parseStatus(status);
		return ResponseEntity.ok(new ApiResponse<>(200, "Success", memberService.list(page, size, search, statusFilter)));
	}

	@GetMapping("/search-by-skill")
	public ResponseEntity<ApiResponse<PageResponse<MemberSearchResponse>>> searchBySkill(
			@RequestParam List<Long> skillIds, @RequestParam(required = false) List<Long> seniorityIds,
			@RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		UserStatus statusFilter = status == null ? null : parseStatus(status);
		List<MemberSearchRequest.SkillCondition> skills = skillConditions(skillIds, seniorityIds);
		MemberSearchRequest request = new MemberSearchRequest(null,
				statusFilter == null ? null : statusFilter.name(), skills, null, page, size);
		return ResponseEntity.ok(new ApiResponse<>(200, "Success", memberService.search(request)));
	}

	@PostMapping("/search")
	public ResponseEntity<ApiResponse<PageResponse<MemberSearchResponse>>> search(
			@RequestBody MemberSearchRequest request) {
		return ResponseEntity.ok(new ApiResponse<>(200, "Success", memberService.search(request)));
	}

	private List<MemberSearchRequest.SkillCondition> skillConditions(List<Long> skillIds, List<Long> seniorityIds) {
		if (skillIds == null || skillIds.isEmpty()
				|| (seniorityIds != null && skillIds.size() != seniorityIds.size())) {
			throw validationError();
		}
		List<MemberSearchRequest.SkillCondition> conditions = new ArrayList<>();
		for (int index = 0; index < skillIds.size(); index++) {
			conditions.add(new MemberSearchRequest.SkillCondition(skillIds.get(index),
					seniorityIds == null ? null : seniorityIds.get(index)));
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
