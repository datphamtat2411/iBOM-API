package com.fpt.ibom.dashboard.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.dashboard.dto.ManagerDashboardStatsResponse;
import com.fpt.ibom.dashboard.dto.MemberDashboardStatsResponse;
import com.fpt.ibom.master.entity.Skill;
import com.fpt.ibom.master.entity.SkillCategory;
import com.fpt.ibom.profile.dto.ProfileSummaryResponse;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.entity.ProfileSkill;
import com.fpt.ibom.profile.repository.ProfileSkillRepository;
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
	private final ProfileSkillRepository profileSkillRepository;
	private final Clock clock;

	public DashboardService(ProfileAccessService profileAccessService,
			ProfileCompletenessService profileCompletenessService, ProfileService profileService,
			ProfileEligibilityService profileEligibilityService, ProfileSkillRepository profileSkillRepository,
			Clock clock) {
		this.profileAccessService = profileAccessService;
		this.profileCompletenessService = profileCompletenessService;
		this.profileService = profileService;
		this.profileEligibilityService = profileEligibilityService;
		this.profileSkillRepository = profileSkillRepository;
		this.clock = clock;
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
		long completedProfiles = profileCompletenessService.countCompleted(eligibleProfiles);
		if (eligibleProfiles.isEmpty()) {
			return new ManagerDashboardStatsResponse(0, completedProfiles, emptyPrimaryDistribution(),
					emptyCategoryDistribution());
		}

		List<Long> profileIds = eligibleProfiles.stream().map(Profile::getId).toList();
		List<ProfileSkill> profileSkills = profileSkillRepository.findByProfileIdIn(profileIds);
		Map<Long, List<ProfileSkill>> skillsByProfile = new HashMap<>();
		LocalDate businessDate = LocalDate.now(clock);
		for (ProfileSkill profileSkill : profileSkills) {
			if (!isValidSkillContribution(profileSkill, businessDate)) {
				continue;
			}
			skillsByProfile.computeIfAbsent(profileSkill.getProfile().getId(), ignored -> new ArrayList<>()).add(profileSkill);
		}

		List<ProfileSkill> primarySkills = new ArrayList<>();
		for (Profile profile : eligibleProfiles) {
			ProfileSkill primarySkill = selectPrimarySkill(skillsByProfile.get(profile.getId()));
			if (primarySkill != null) {
				primarySkills.add(primarySkill);
			}
		}

		return new ManagerDashboardStatsResponse(eligibleProfiles.size(), completedProfiles,
				buildPrimaryDistribution(primarySkills), buildCategoryDistribution(primarySkills));
	}

	private boolean isValidSkillContribution(ProfileSkill profileSkill, LocalDate businessDate) {
		if (profileSkill == null || profileSkill.getProfile() == null || profileSkill.getProfile().getId() == null) {
			return false;
		}
		Skill skill = profileSkill.getSkill();
		return skill != null && skill.getId() != null && skill.getName() != null && !skill.getName().isBlank()
				&& profileSkill.getExperienceYears() != null
				&& profileSkill.getExperienceYears().compareTo(BigDecimal.ZERO) >= 0
				&& (profileSkill.getLastUsed() == null || !profileSkill.getLastUsed().isAfter(businessDate));
	}

	private ProfileSkill selectPrimarySkill(List<ProfileSkill> profileSkills) {
		if (profileSkills == null || profileSkills.isEmpty()) {
			return null;
		}
		Comparator<ProfileSkill> comparator = Comparator
				.comparing(ProfileSkill::getExperienceYears,
						Comparator.nullsLast(Comparator.reverseOrder()))
				.thenComparing(ProfileSkill::getLastUsed, Comparator.nullsLast(Comparator.reverseOrder()))
				.thenComparing(profileSkill -> profileSkill.getSkill().getName(),
						Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
				.thenComparing(profileSkill -> profileSkill.getSkill().getId(),
						Comparator.nullsLast(Comparator.naturalOrder()));
		return profileSkills.stream().min(comparator).orElse(null);
	}

	private ManagerDashboardStatsResponse.PrimarySkillDistribution buildPrimaryDistribution(
			List<ProfileSkill> primarySkills) {
		Map<Long, List<ProfileSkill>> skillsById = new HashMap<>();
		for (ProfileSkill profileSkill : primarySkills) {
			skillsById.computeIfAbsent(profileSkill.getSkill().getId(), ignored -> new ArrayList<>()).add(profileSkill);
		}
		List<ManagerDashboardStatsResponse.PrimarySkillItem> sortedItems = skillsById.values().stream()
				.map(skills -> new ManagerDashboardStatsResponse.PrimarySkillItem(skills.get(0).getSkill().getId(),
						skills.get(0).getSkill().getName(), skills.size()))
				.sorted(Comparator.comparing(ManagerDashboardStatsResponse.PrimarySkillItem::profileCount,
						Comparator.reverseOrder())
						.thenComparing(ManagerDashboardStatsResponse.PrimarySkillItem::skillName,
								Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
						.thenComparing(ManagerDashboardStatsResponse.PrimarySkillItem::skillId,
								Comparator.nullsLast(Comparator.naturalOrder())))
				.toList();
		return new ManagerDashboardStatsResponse.PrimarySkillDistribution(sortedItems.stream().limit(7).toList(),
				sortedItems.stream().skip(7).mapToLong(ManagerDashboardStatsResponse.PrimarySkillItem::profileCount).sum());
	}

	private ManagerDashboardStatsResponse.SkillCategoryDistribution buildCategoryDistribution(
			List<ProfileSkill> primarySkills) {
		if (primarySkills.isEmpty()) {
			return emptyCategoryDistribution();
		}
		Map<CategoryKey, Long> categoryCounts = new HashMap<>();
		for (ProfileSkill profileSkill : primarySkills) {
			CategoryKey category = categoryKey(profileSkill);
			categoryCounts.merge(category, 1L, Long::sum);
		}
		List<ManagerDashboardStatsResponse.SkillCategoryItem> sortedItems = categoryCounts.entrySet().stream()
				.map(entry -> new ManagerDashboardStatsResponse.SkillCategoryItem(entry.getKey().id(),
						entry.getKey().code(), entry.getKey().name(), entry.getValue(),
						percentage(entry.getValue(), primarySkills.size())))
				.sorted(Comparator.comparing(ManagerDashboardStatsResponse.SkillCategoryItem::profileCount,
						Comparator.reverseOrder())
						.thenComparing(ManagerDashboardStatsResponse.SkillCategoryItem::categoryName,
								Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
						.thenComparing(ManagerDashboardStatsResponse.SkillCategoryItem::categoryId,
								Comparator.nullsLast(Comparator.naturalOrder())))
				.toList();
		return new ManagerDashboardStatsResponse.SkillCategoryDistribution(sortedItems.stream().limit(7).toList(),
				sortedItems.stream().skip(7).mapToLong(ManagerDashboardStatsResponse.SkillCategoryItem::profileCount).sum());
	}

	private CategoryKey categoryKey(ProfileSkill profileSkill) {
		SkillCategory category = profileSkill.getSkill().getCategory();
		if (category == null || category.getId() == null || category.getCode() == null || category.getCode().isBlank()
				|| category.getName() == null || category.getName().isBlank()) {
			return new CategoryKey(null, null, "Uncategorized");
		}
		return new CategoryKey(category.getId(), category.getCode(), category.getName());
	}

	private int percentage(long count, long denominator) {
		return BigDecimal.valueOf(count).multiply(BigDecimal.valueOf(100))
				.divide(BigDecimal.valueOf(denominator), 0, RoundingMode.HALF_UP).intValueExact();
	}

	private ManagerDashboardStatsResponse.PrimarySkillDistribution emptyPrimaryDistribution() {
		return new ManagerDashboardStatsResponse.PrimarySkillDistribution(List.of(), 0);
	}

	private ManagerDashboardStatsResponse.SkillCategoryDistribution emptyCategoryDistribution() {
		return new ManagerDashboardStatsResponse.SkillCategoryDistribution(List.of(), 0);
	}

	private record CategoryKey(Long id, String code, String name) {
	}
}
