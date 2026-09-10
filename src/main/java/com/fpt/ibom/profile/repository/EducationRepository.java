package com.fpt.ibom.profile.repository;

import java.util.List;
import java.util.Optional;

import com.fpt.ibom.profile.entity.Education;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EducationRepository extends JpaRepository<Education, Long> {

	List<Education> findByProfileIdOrderByIdAsc(Long profileId);

	Optional<Education> findByIdAndProfileId(Long educationId, Long profileId);
}
