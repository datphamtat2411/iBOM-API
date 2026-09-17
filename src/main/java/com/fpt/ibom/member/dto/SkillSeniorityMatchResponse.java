package com.fpt.ibom.member.dto;

import java.math.BigDecimal;

public record SkillSeniorityMatchResponse(Long skillId, Long seniorityId, BigDecimal experienceYears) {
}
