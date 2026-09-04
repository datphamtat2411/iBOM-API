package com.fpt.ibom.profile.entity;

import java.math.BigDecimal;
import java.time.Instant;

import com.fpt.ibom.auth.entity.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "profiles")
public class Profile {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private UserAccount user;

	@Column(name = "profile_name", nullable = false, length = 100)
	private String profileName;
	@Column(name = "first_name", nullable = false, length = 100)
	private String firstName;
	@Column(name = "last_name", nullable = false, length = 100)
	private String lastName;
	@Column(name = "job_title", nullable = false, length = 100)
	private String jobTitle;
	@Column(nullable = false, length = 4000)
	private String personality;
	@Column(name = "technical_summary", nullable = false, length = 4000)
	private String technicalSummary;
	@Column(name = "years_of_experience", nullable = false, precision = 5, scale = 2)
	private BigDecimal yearsOfExperience;
	@Column(name = "has_previewed", nullable = false)
	private boolean hasPreviewed = false;
	@Column(name = "deleted_at")
	private Instant deletedAt;
	@Version
	@Column(nullable = false)
	private long version;
	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Profile() {
	}

	public Profile(UserAccount user, String profileName, String firstName, String lastName, String jobTitle,
			BigDecimal yearsOfExperience, String personality, String technicalSummary) {
		this.user = user;
		this.profileName = profileName;
		this.firstName = firstName;
		this.lastName = lastName;
		this.jobTitle = jobTitle;
		this.yearsOfExperience = yearsOfExperience;
		this.personality = personality;
		this.technicalSummary = technicalSummary;
	}

	public Long getId() { return id; }
	public UserAccount getUser() { return user; }
	public String getProfileName() { return profileName; }
	public String getFirstName() { return firstName; }
	public String getLastName() { return lastName; }
	public String getJobTitle() { return jobTitle; }
	public String getPersonality() { return personality; }
	public String getTechnicalSummary() { return technicalSummary; }
	public BigDecimal getYearsOfExperience() { return yearsOfExperience; }
	public boolean isHasPreviewed() { return hasPreviewed; }
	public Instant getDeletedAt() { return deletedAt; }
	public long getVersion() { return version; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }

	public void softDelete(Instant deletedAt) { this.deletedAt = deletedAt; }

	public void update(String profileName, String firstName, String lastName, String jobTitle,
			BigDecimal yearsOfExperience, String personality, String technicalSummary) {
		this.profileName = profileName;
		this.firstName = firstName;
		this.lastName = lastName;
		this.jobTitle = jobTitle;
		this.yearsOfExperience = yearsOfExperience;
		this.personality = personality;
		this.technicalSummary = technicalSummary;
		this.hasPreviewed = false;
	}
}
