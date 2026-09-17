package com.fpt.ibom.master.dto;

import com.fpt.ibom.master.entity.SkillCategory;

public record SkillCategoryResponse(Long id, String code, String name) {

	public static SkillCategoryResponse from(SkillCategory category) {
		return new SkillCategoryResponse(category.getId(), category.getCode(), category.getName());
	}
}
