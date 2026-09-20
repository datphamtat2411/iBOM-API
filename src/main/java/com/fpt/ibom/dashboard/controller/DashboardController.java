package com.fpt.ibom.dashboard.controller;

import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.common.ApiResponse;
import com.fpt.ibom.dashboard.dto.ManagerDashboardStatsResponse;
import com.fpt.ibom.dashboard.dto.MemberDashboardStatsResponse;
import com.fpt.ibom.dashboard.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

	private final DashboardService dashboardService;

	public DashboardController(DashboardService dashboardService) {
		this.dashboardService = dashboardService;
	}

	@GetMapping("/my-stats")
	public ResponseEntity<ApiResponse<MemberDashboardStatsResponse>> getStats(
			@AuthenticationPrincipal UserPrincipal principal, @RequestParam Long profileId) {
		return ResponseEntity.ok(new ApiResponse<>(200, "Success", dashboardService.getStats(principal, profileId)));
	}

	@GetMapping("/manager-stats")
	public ResponseEntity<ApiResponse<ManagerDashboardStatsResponse>> getManagerStats() {
		return ResponseEntity.ok(new ApiResponse<>(200, "Success", dashboardService.getManagerStats()));
	}
}
