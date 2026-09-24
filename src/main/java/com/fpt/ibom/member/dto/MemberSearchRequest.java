package com.fpt.ibom.member.dto;

import java.util.List;

public record MemberSearchRequest(String search, String status, List<SkillCondition> skills,
		List<LanguageCondition> languages, Integer page, Integer size) {

	public record SkillCondition(Long skillId, Long seniorityId) {
	}

	public record LanguageCondition(Long languageId, String level) {
	}
}
