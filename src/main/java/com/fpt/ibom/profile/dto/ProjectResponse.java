package com.fpt.ibom.profile.dto;

import java.time.LocalDate;

import com.fpt.ibom.profile.entity.Project;
import com.fpt.ibom.profile.entity.ProjectStatus;

public record ProjectResponse(Long id, String name, String description, LocalDate startDate, LocalDate endDate,
		ProjectStatus status, String position, Integer teamSize, String responsibilities, String programmingLanguages,
		String tools) {

	public static ProjectResponse from(Project project) {
		return new ProjectResponse(project.getId(), project.getName(), project.getDescription(), project.getStartDate(),
				project.getEndDate(), project.getStatus(), project.getPosition(), project.getTeamSize(),
				project.getResponsibilities(), project.getProgrammingLanguages(), project.getTools());
	}
}
