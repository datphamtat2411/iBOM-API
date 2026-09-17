package com.fpt.ibom.master.repository;

import java.util.List;
import java.util.Optional;

import com.fpt.ibom.master.entity.SkillCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillCategoryRepository extends JpaRepository<SkillCategory, Long> {

	List<SkillCategory> findAllByOrderByIdAsc();

	Optional<SkillCategory> findByCode(String code);
}
