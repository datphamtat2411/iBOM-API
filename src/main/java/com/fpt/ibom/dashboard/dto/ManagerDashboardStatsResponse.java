package com.fpt.ibom.dashboard.dto;

import java.util.List;

public record ManagerDashboardStatsResponse(long totalProfiles, long completedProfiles,
		PrimarySkillDistribution primarySkillDistribution, SkillCategoryDistribution skillCategoryDistribution) {

	public ManagerDashboardStatsResponse(long totalProfiles, long completedProfiles) {
		this(totalProfiles, completedProfiles, new PrimarySkillDistribution(List.of(), 0),
				new SkillCategoryDistribution(List.of(), 0));
	}

	public record PrimarySkillDistribution(List<PrimarySkillItem> items, long otherProfileCount) {
	}

	public record PrimarySkillItem(Long skillId, String skillName, long profileCount) {
	}

	public record SkillCategoryDistribution(List<SkillCategoryItem> items, long otherProfileCount) {
	}

	public record SkillCategoryItem(Long categoryId, String categoryCode, String categoryName, long profileCount,
			int percentage) {
	}
}
