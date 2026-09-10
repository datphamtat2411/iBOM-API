package com.fpt.ibom.profile.entity;

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

@Entity
@Table(name = "educations")
public class Education {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "profile_id", nullable = false)
	private Profile profile;

	@Column(name = "school_name", nullable = false, length = 255)
	private String schoolName;
	@Column(nullable = false, length = 255)
	private String degree;
	@Column(name = "field_of_study", length = 255)
	private String fieldOfStudy;
	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;
	@Column(name = "end_date")
	private LocalDate endDate;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EducationStatus status;

	protected Education() {
	}

	public Education(Profile profile, String schoolName, String degree, String fieldOfStudy, LocalDate startDate,
			LocalDate endDate, EducationStatus status) {
		this.profile = profile;
		this.schoolName = schoolName;
		this.degree = degree;
		this.fieldOfStudy = fieldOfStudy;
		this.startDate = startDate;
		this.endDate = endDate;
		this.status = status;
	}

	public Long getId() { return id; }
	public Profile getProfile() { return profile; }
	public String getSchoolName() { return schoolName; }
	public String getDegree() { return degree; }
	public String getFieldOfStudy() { return fieldOfStudy; }
	public LocalDate getStartDate() { return startDate; }
	public LocalDate getEndDate() { return endDate; }
	public EducationStatus getStatus() { return status; }

	public void update(String schoolName, String degree, String fieldOfStudy, LocalDate startDate, LocalDate endDate,
			EducationStatus status) {
		this.schoolName = schoolName;
		this.degree = degree;
		this.fieldOfStudy = fieldOfStudy;
		this.startDate = startDate;
		this.endDate = endDate;
		this.status = status;
	}
}
