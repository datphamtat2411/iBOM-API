package com.fpt.ibom.profile.entity;

import java.time.Instant;
import java.time.LocalDate;

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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "certificates", uniqueConstraints = @UniqueConstraint(name = "uk_certificates_profile_name_issue_date", columnNames = {
		"profile_id", "certificate_name", "issue_date" }))
public class Certificate {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "profile_id", nullable = false)
	private Profile profile;

	@Column(name = "certificate_name", nullable = false, length = 255)
	private String certificateName;
	@Column(name = "issue_date", nullable = false)
	private LocalDate issueDate;
	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Certificate() {
	}

	public Certificate(Profile profile, String certificateName, LocalDate issueDate) {
		this.profile = profile;
		this.certificateName = certificateName;
		this.issueDate = issueDate;
	}

	public Long getId() { return id; }
	public Profile getProfile() { return profile; }
	public String getCertificateName() { return certificateName; }
	public LocalDate getIssueDate() { return issueDate; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }

	public void update(String certificateName, LocalDate issueDate) {
		this.certificateName = certificateName;
		this.issueDate = issueDate;
	}
}
