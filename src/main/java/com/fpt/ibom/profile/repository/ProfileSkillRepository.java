package com.fpt.ibom.profile.repository;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

import com.fpt.ibom.profile.entity.ProfileSkill;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfileSkillRepository extends JpaRepository<ProfileSkill, Long> {

	@EntityGraph(attributePaths = { "skill", "skill.category" })
	List<ProfileSkill> findByProfileId(Long profileId);

	boolean existsByProfileId(Long profileId);

	@Query("select profileSkill.profile.id from ProfileSkill profileSkill where profileSkill.profile.id in :profileIds")
	List<Long> findProfileIdsByProfileIdIn(@Param("profileIds") List<Long> profileIds);

	Optional<ProfileSkill> findByIdAndProfileId(Long profileSkillId, Long profileId);

	boolean existsByProfileIdAndSkillId(Long profileId, Long skillId);

	boolean existsBySkillId(Long skillId);

	boolean existsByProfileIdAndSkillIdAndIdNot(Long profileId, Long skillId, Long profileSkillId);

	boolean existsByExperienceYearsGreaterThanEqualAndExperienceYearsLessThan(BigDecimal fromExperience,
			BigDecimal toExperience);

	boolean existsByExperienceYearsGreaterThanEqual(BigDecimal fromExperience);
}
