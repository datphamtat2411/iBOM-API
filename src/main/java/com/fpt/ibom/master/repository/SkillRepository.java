package com.fpt.ibom.master.repository;

import com.fpt.ibom.master.entity.Skill;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillRepository extends JpaRepository<Skill, Long> {

	@Override
	@EntityGraph(attributePaths = "category")
	Page<Skill> findAll(Pageable pageable);

	@EntityGraph(attributePaths = "category")
	Page<Skill> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
