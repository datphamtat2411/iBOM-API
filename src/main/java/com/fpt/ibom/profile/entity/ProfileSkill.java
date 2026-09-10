package com.fpt.ibom.profile.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fpt.ibom.master.entity.Skill;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "profile_skills", uniqueConstraints = @UniqueConstraint(name = "uk_profile_skills_profile_skill", columnNames = {
		"profile_id", "skill_id" }))
public class ProfileSkill {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "profile_id", nullable = false)
	private Profile profile;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "skill_id", nullable = false)
	private Skill skill;

	@Column(name = "experience_years", nullable = false, precision = 5, scale = 2)
	private BigDecimal experienceYears;

	@Column(name = "last_used")
	private LocalDate lastUsed;

	protected ProfileSkill() {
	}

	public ProfileSkill(Profile profile, Skill skill, BigDecimal experienceYears, LocalDate lastUsed) {
		this.profile = profile;
		this.skill = skill;
		this.experienceYears = experienceYears;
		this.lastUsed = lastUsed;
	}

	public Long getId() { return id; }
	public Profile getProfile() { return profile; }
	public Skill getSkill() { return skill; }
	public BigDecimal getExperienceYears() { return experienceYears; }
	public LocalDate getLastUsed() { return lastUsed; }

	public void update(Skill skill, BigDecimal experienceYears, LocalDate lastUsed) {
		this.skill = skill;
		this.experienceYears = experienceYears;
		this.lastUsed = lastUsed;
	}
}
