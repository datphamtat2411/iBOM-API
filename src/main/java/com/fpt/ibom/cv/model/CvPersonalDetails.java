package com.fpt.ibom.cv.model;

import java.math.BigDecimal;

public record CvPersonalDetails(String firstName, String lastName, String jobTitle, BigDecimal yearsOfExperience,
		String personality, String technicalSummary) {
}
