package com.fpt.ibom.profile.controller;

import java.util.List;

import com.fpt.ibom.common.ApiResponse;
import com.fpt.ibom.profile.dto.ProfileSummaryResponse;
import com.fpt.ibom.profile.service.ProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
public class MemberProfileController {

	private final ProfileService profileService;

	public MemberProfileController(ProfileService profileService) {
		this.profileService = profileService;
	}

	@GetMapping("/{memberId}/profiles")
	public ResponseEntity<ApiResponse<List<ProfileSummaryResponse>>> list(@PathVariable Long memberId) {
		return ResponseEntity.ok(new ApiResponse<>(200, "Success", profileService.listMemberProfiles(memberId)));
	}
}
