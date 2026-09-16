package com.fpt.ibom.cv.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CvSkill(String skillName, String categoryCode, String categoryName, BigDecimal experienceYears,
		LocalDate lastUsed) {
}
