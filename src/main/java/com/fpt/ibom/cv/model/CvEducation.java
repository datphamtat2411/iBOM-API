package com.fpt.ibom.cv.model;

import java.time.LocalDate;

public record CvEducation(String schoolName, String degree, String fieldOfStudy, LocalDate startDate, LocalDate endDate,
		CvEducationStatus status) {
}
