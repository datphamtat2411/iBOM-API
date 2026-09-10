package com.fpt.ibom.master.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "skill_categories")
public class SkillCategory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String code;

	@Column(nullable = false, length = 255)
	private String name;

	protected SkillCategory() {
	}

	public SkillCategory(String code, String name) {
		this.code = code == null ? null : code.trim();
		this.name = name == null ? null : name.trim();
	}

	public Long getId() { return id; }
	public String getCode() { return code; }
	public String getName() { return name; }
}
