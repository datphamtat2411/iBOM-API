package com.fpt.ibom.member.dto;

import java.util.List;

import com.fpt.ibom.auth.entity.UserStatus;

public record MemberSkillSearchRequest(List<Long> skillIds, List<Long> seniorityIds, UserStatus status, int page,
		int size) {
}
