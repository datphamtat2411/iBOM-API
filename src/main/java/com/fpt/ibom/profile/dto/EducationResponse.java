package com.fpt.ibom.profile.dto;

import java.time.LocalDate;

import com.fpt.ibom.profile.entity.Education;
import com.fpt.ibom.profile.entity.EducationStatus;

public record EducationResponse(Long id, String schoolName, String degree, String fieldOfStudy, LocalDate startDate,
		LocalDate endDate, EducationStatus status) {

	public static EducationResponse from(Education education) {
		return new EducationResponse(education.getId(), education.getSchoolName(), education.getDegree(),
				education.getFieldOfStudy(), education.getStartDate(), education.getEndDate(), education.getStatus());
	}
}
