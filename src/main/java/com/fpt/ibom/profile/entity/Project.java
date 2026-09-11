package com.fpt.ibom.profile.entity;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "projects")
public class Project {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "profile_id", nullable = false)
	private Profile profile;

	@Column(nullable = false, length = 255)
	private String name;
	@Column(nullable = false, columnDefinition = "TEXT")
	private String description;
	@Column(name = "start_date")
	private LocalDate startDate;
	@Column(name = "end_date")
	private LocalDate endDate;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ProjectStatus status;
	@Column(nullable = false, length = 255)
	private String position;
	@Column(name = "team_size")
	private Integer teamSize;
	@Column(columnDefinition = "TEXT")
	private String responsibilities;
	@Column(name = "programming_languages", columnDefinition = "TEXT")
	private String programmingLanguages;
	@Column(columnDefinition = "TEXT")
	private String tools;
	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Project() {
	}

	public Project(Profile profile, String name, String description, LocalDate startDate, LocalDate endDate,
			ProjectStatus status, String position, Integer teamSize, String responsibilities, String programmingLanguages,
			String tools) {
		this.profile = profile;
		this.name = name;
		this.description = description;
		this.startDate = startDate;
		this.endDate = endDate;
		this.status = status;
		this.position = position;
		this.teamSize = teamSize;
		this.responsibilities = responsibilities;
		this.programmingLanguages = programmingLanguages;
		this.tools = tools;
	}

	public Long getId() { return id; }
	public Profile getProfile() { return profile; }
	public String getName() { return name; }
	public String getDescription() { return description; }
	public LocalDate getStartDate() { return startDate; }
	public LocalDate getEndDate() { return endDate; }
	public ProjectStatus getStatus() { return status; }
	public String getPosition() { return position; }
	public Integer getTeamSize() { return teamSize; }
	public String getResponsibilities() { return responsibilities; }
	public String getProgrammingLanguages() { return programmingLanguages; }
	public String getTools() { return tools; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }

	public void update(String name, String description, LocalDate startDate, LocalDate endDate, ProjectStatus status,
			String position, Integer teamSize, String responsibilities, String programmingLanguages, String tools) {
		this.name = name;
		this.description = description;
		this.startDate = startDate;
		this.endDate = endDate;
		this.status = status;
		this.position = position;
		this.teamSize = teamSize;
		this.responsibilities = responsibilities;
		this.programmingLanguages = programmingLanguages;
		this.tools = tools;
	}
}
