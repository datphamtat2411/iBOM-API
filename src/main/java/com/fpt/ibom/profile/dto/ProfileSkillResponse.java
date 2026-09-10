package com.fpt.ibom.profile.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fpt.ibom.profile.entity.ProfileSkill;

public record ProfileSkillResponse(Long profileSkillId, Long skillId, String skillName, Long categoryId,
		String categoryCode, String categoryName, BigDecimal experienceYears, LocalDate lastUsed) {

	public static ProfileSkillResponse from(ProfileSkill profileSkill) {
		return new ProfileSkillResponse(profileSkill.getId(), profileSkill.getSkill().getId(),
				profileSkill.getSkill().getName(), profileSkill.getSkill().getCategory().getId(),
				profileSkill.getSkill().getCategory().getCode(), profileSkill.getSkill().getCategory().getName(),
				profileSkill.getExperienceYears(), profileSkill.getLastUsed());
	}
}
