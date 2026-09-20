package com.fpt.ibom.dashboard.service;

import java.util.List;

import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.dashboard.dto.ManagerDashboardStatsResponse;
import com.fpt.ibom.dashboard.dto.MemberDashboardStatsResponse;
import com.fpt.ibom.profile.dto.ProfileSummaryResponse;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.service.ProfileAccessService;
import com.fpt.ibom.profile.service.ProfileCompletenessService;
import com.fpt.ibom.profile.service.ProfileEligibilityService;
import com.fpt.ibom.profile.service.ProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

	private final ProfileAccessService profileAccessService;
	private final ProfileCompletenessService profileCompletenessService;
	private final ProfileService profileService;
	private final ProfileEligibilityService profileEligibilityService;

	public DashboardService(ProfileAccessService profileAccessService,
			ProfileCompletenessService profileCompletenessService, ProfileService profileService,
			ProfileEligibilityService profileEligibilityService) {
		this.profileAccessService = profileAccessService;
		this.profileCompletenessService = profileCompletenessService;
		this.profileService = profileService;
		this.profileEligibilityService = profileEligibilityService;
	}

	@Transactional(readOnly = true)
	public MemberDashboardStatsResponse getStats(UserPrincipal principal, Long profileId) {
		Profile selectedProfile = profileAccessService.resolveOwned(principal, profileId);
		return new MemberDashboardStatsResponse(ProfileSummaryResponse.from(selectedProfile),
				profileCompletenessService.calculate(selectedProfile), profileService.latestExportedAt(principal.userId()));
	}

	@Transactional(readOnly = true)
	public ManagerDashboardStatsResponse getManagerStats() {
		List<Profile> eligibleProfiles = profileEligibilityService.findEligibleMemberProfiles();
		return new ManagerDashboardStatsResponse(eligibleProfiles.size(),
				profileCompletenessService.countCompleted(eligibleProfiles));
	}
}
