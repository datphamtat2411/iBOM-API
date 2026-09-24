package com.fpt.ibom.master.repository;

import java.util.Optional;

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

	@EntityGraph(attributePaths = "category")
	Page<Skill> findByCategoryId(Long categoryId, Pageable pageable);

	@EntityGraph(attributePaths = "category")
	Page<Skill> findByCategoryIdAndNameContainingIgnoreCase(Long categoryId, String name, Pageable pageable);

	boolean existsByNameIgnoreCase(String name);

	boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

	Optional<Skill> findByNameIgnoreCase(String name);
}
