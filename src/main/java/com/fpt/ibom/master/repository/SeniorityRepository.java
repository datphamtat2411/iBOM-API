package com.fpt.ibom.master.repository;

import java.util.List;

import com.fpt.ibom.master.entity.Seniority;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SeniorityRepository extends JpaRepository<Seniority, Long> {

	boolean existsByNameIgnoreCase(String name);

	boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

	List<Seniority> findAllByOrderByFromExperienceAscIdAsc();

	@Query(value = "SELECT id FROM seniority_mutation_lock WHERE id = 1 FOR UPDATE", nativeQuery = true)
	Long lockMutationSection();
}
