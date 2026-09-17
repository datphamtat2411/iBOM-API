package com.fpt.ibom.master.dto;

import java.math.BigDecimal;

import com.fpt.ibom.master.entity.Seniority;

public record SeniorityResponse(Long id, String name, BigDecimal fromExperience, BigDecimal toExperience) {

	public static SeniorityResponse from(Seniority seniority) {
		return new SeniorityResponse(seniority.getId(), seniority.getName(), seniority.getFromExperience(),
				seniority.getToExperience());
	}
}
