package com.fpt.ibom.master.repository;

import java.util.List;

import com.fpt.ibom.master.entity.Seniority;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeniorityRepository extends JpaRepository<Seniority, Long> {

	boolean existsByNameIgnoreCase(String name);

	boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

	List<Seniority> findAllByOrderByFromExperienceAscIdAsc();
}
