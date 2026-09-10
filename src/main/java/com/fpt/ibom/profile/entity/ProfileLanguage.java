package com.fpt.ibom.profile.entity;

import java.time.Instant;

import com.fpt.ibom.master.entity.Language;
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
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "profile_languages", uniqueConstraints = @UniqueConstraint(name = "uk_profile_languages_profile_language", columnNames = {
		"profile_id", "language_id" }))
public class ProfileLanguage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "profile_id", nullable = false)
	private Profile profile;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "language_id", nullable = false)
	private Language language;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private LanguageLevel level;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ProfileLanguage() {
	}

	public ProfileLanguage(Profile profile, Language language, LanguageLevel level) {
		this.profile = profile;
		this.language = language;
		this.level = level;
	}

	public Long getId() { return id; }
	public Profile getProfile() { return profile; }
	public Language getLanguage() { return language; }
	public LanguageLevel getLevel() { return level; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }

	public void update(Language language, LanguageLevel level) {
		this.language = language;
		this.level = level;
	}
}
