package com.fpt.ibom.dashboard.service;

import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.dashboard.dto.MemberDashboardStatsResponse;
import com.fpt.ibom.profile.dto.ProfileSummaryResponse;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.service.ProfileAccessService;
import com.fpt.ibom.profile.service.ProfileCompletenessService;
import com.fpt.ibom.profile.service.ProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

	private final ProfileAccessService profileAccessService;
	private final ProfileCompletenessService profileCompletenessService;
	private final ProfileService profileService;

	public DashboardService(ProfileAccessService profileAccessService,
			ProfileCompletenessService profileCompletenessService, ProfileService profileService) {
		this.profileAccessService = profileAccessService;
		this.profileCompletenessService = profileCompletenessService;
		this.profileService = profileService;
	}

	@Transactional(readOnly = true)
	public MemberDashboardStatsResponse getStats(UserPrincipal principal, Long profileId) {
		Profile selectedProfile = profileAccessService.resolveOwned(principal, profileId);
		return new MemberDashboardStatsResponse(ProfileSummaryResponse.from(selectedProfile),
				profileCompletenessService.calculate(selectedProfile), profileService.latestExportedAt(principal.userId()));
	}
}
