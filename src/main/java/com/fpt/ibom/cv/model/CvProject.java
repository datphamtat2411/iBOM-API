package com.fpt.ibom.cv.model;

import java.time.LocalDate;

public record CvProject(String name, String description, LocalDate startDate, LocalDate endDate, CvProjectStatus status,
		String position, Integer teamSize, String responsibilities, String programmingLanguages, String tools) {
}
