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
	@Column(name = "full_name", nullable = false)
	private String fullName;
	@Column(name = "job_title", nullable = false)
	private String jobTitle;
	@Column(nullable = false)
	private String email;
	@Column(name = "phone_number", nullable = false, length = 50)
	private String phoneNumber;
	@Column(nullable = false, length = 500)
	private String address;
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

	public Profile(UserAccount user, String profileName, String fullName, String jobTitle, String email,
			String phoneNumber, String address, BigDecimal yearsOfExperience) {
		this.user = user;
		this.profileName = profileName;
		this.fullName = fullName;
		this.jobTitle = jobTitle;
		this.email = email;
		this.phoneNumber = phoneNumber;
		this.address = address;
		this.yearsOfExperience = yearsOfExperience;
	}

	public Long getId() { return id; }
	public UserAccount getUser() { return user; }
	public String getProfileName() { return profileName; }
	public String getFullName() { return fullName; }
	public String getJobTitle() { return jobTitle; }
	public String getEmail() { return email; }
	public String getPhoneNumber() { return phoneNumber; }
	public String getAddress() { return address; }
	public BigDecimal getYearsOfExperience() { return yearsOfExperience; }
	public boolean isHasPreviewed() { return hasPreviewed; }
	public Instant getDeletedAt() { return deletedAt; }
	public long getVersion() { return version; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }

	public void softDelete(Instant deletedAt) { this.deletedAt = deletedAt; }
}
