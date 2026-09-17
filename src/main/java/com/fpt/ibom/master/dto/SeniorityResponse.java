package com.fpt.ibom.master.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fpt.ibom.master.entity.Seniority;

public record SeniorityResponse(Long id, String name, BigDecimal fromExperience, BigDecimal toExperience, Instant createdAt,
		Instant updatedAt) {

	public static SeniorityResponse from(Seniority seniority) {
		return new SeniorityResponse(seniority.getId(), seniority.getName(), seniority.getFromExperience(),
				seniority.getToExperience(), seniority.getCreatedAt(), seniority.getUpdatedAt());
	}
}
