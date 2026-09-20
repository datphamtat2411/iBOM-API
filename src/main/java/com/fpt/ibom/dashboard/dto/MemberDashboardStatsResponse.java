package com.fpt.ibom.dashboard.dto;

import java.time.Instant;

import com.fpt.ibom.profile.dto.ProfileCompletenessResponse;
import com.fpt.ibom.profile.dto.ProfileSummaryResponse;

public record MemberDashboardStatsResponse(ProfileSummaryResponse selectedProfile,
		ProfileCompletenessResponse completeness, Instant latestExportedAt) {
}
