package com.fpt.ibom.master.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "file_name_formats")
public class FileNameFormat {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 255)
	private String name;

	@Column(nullable = false, length = 1000)
	private String pattern;

	@Column(name = "is_default", nullable = false)
	private boolean isDefault;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected FileNameFormat() {
	}

	public FileNameFormat(String name, String pattern, boolean isDefault) {
		this.name = name == null ? null : name.trim();
		this.pattern = pattern == null ? null : pattern.trim();
		this.isDefault = isDefault;
	}

	public Long getId() { return id; }
	public String getName() { return name; }
	public String getPattern() { return pattern; }
	public boolean isDefault() { return isDefault; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }

	public void update(String name, String pattern) {
		this.name = name.trim();
		this.pattern = pattern.trim();
	}
}
