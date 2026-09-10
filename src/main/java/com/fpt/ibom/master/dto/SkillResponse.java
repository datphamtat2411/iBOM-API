package com.fpt.ibom.master.dto;

import java.time.Instant;

import com.fpt.ibom.master.entity.Skill;

public record SkillResponse(Long id, String name, Long categoryId, String categoryCode, String categoryName,
		Instant createdAt, Instant updatedAt) {

	public static SkillResponse from(Skill skill) {
		return new SkillResponse(skill.getId(), skill.getName(), skill.getCategory().getId(),
				skill.getCategory().getCode(), skill.getCategory().getName(), skill.getCreatedAt(), skill.getUpdatedAt());
	}
}
