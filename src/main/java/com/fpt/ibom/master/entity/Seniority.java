package com.fpt.ibom.master.entity;

import java.math.BigDecimal;
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
@Table(name = "seniorities")
public class Seniority {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 255)
	private String name;

	@Column(name = "from_experience", nullable = false, precision = 5, scale = 2)
	private BigDecimal fromExperience;

	@Column(name = "to_experience", precision = 5, scale = 2)
	private BigDecimal toExperience;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Seniority() {
	}

	public Seniority(String name, BigDecimal fromExperience, BigDecimal toExperience) {
		this.name = name == null ? null : name.trim();
		this.fromExperience = fromExperience;
		this.toExperience = toExperience;
	}

	public Long getId() { return id; }
	public String getName() { return name; }
	public BigDecimal getFromExperience() { return fromExperience; }
	public BigDecimal getToExperience() { return toExperience; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }

	public void update(String name, BigDecimal fromExperience, BigDecimal toExperience) {
		this.name = name.trim();
		this.fromExperience = fromExperience;
		this.toExperience = toExperience;
	}
}
