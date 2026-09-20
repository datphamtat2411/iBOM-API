package com.fpt.ibom.profile.repository;

import java.util.List;
import java.util.Optional;

import com.fpt.ibom.profile.entity.Education;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EducationRepository extends JpaRepository<Education, Long> {

	List<Education> findByProfileIdOrderByIdAsc(Long profileId);

	boolean existsByProfileId(Long profileId);

	@Query("select education.profile.id from Education education where education.profile.id in :profileIds")
	List<Long> findProfileIdsByProfileIdIn(@Param("profileIds") List<Long> profileIds);

	Optional<Education> findByIdAndProfileId(Long educationId, Long profileId);
}
