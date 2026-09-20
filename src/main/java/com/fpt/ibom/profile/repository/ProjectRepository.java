package com.fpt.ibom.profile.repository;

import java.util.List;
import java.util.Optional;

import com.fpt.ibom.profile.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, Long> {

	@Query("SELECT p FROM Project p WHERE p.profile.id = :profileId "
			+ "ORDER BY CASE WHEN p.status = com.fpt.ibom.profile.entity.ProjectStatus.ONGOING THEN 0 ELSE 1 END, "
			+ "p.endDate DESC, p.startDate DESC, p.createdAt DESC")
	List<Project> findByProfileIdInDisplayOrder(@Param("profileId") Long profileId);

	boolean existsByProfileId(Long profileId);

	@Query("select project.profile.id from Project project where project.profile.id in :profileIds")
	List<Long> findProfileIdsByProfileIdIn(@Param("profileIds") List<Long> profileIds);

	Optional<Project> findByIdAndProfileId(Long projectId, Long profileId);
}
