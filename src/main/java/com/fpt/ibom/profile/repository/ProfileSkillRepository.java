package com.fpt.ibom.profile.repository;

import java.util.List;
import java.util.Optional;

import com.fpt.ibom.profile.entity.ProfileSkill;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileSkillRepository extends JpaRepository<ProfileSkill, Long> {

	@EntityGraph(attributePaths = { "skill", "skill.category" })
	List<ProfileSkill> findByProfileId(Long profileId);

	Optional<ProfileSkill> findByIdAndProfileId(Long profileSkillId, Long profileId);

	boolean existsByProfileIdAndSkillId(Long profileId, Long skillId);

	boolean existsByProfileIdAndSkillIdAndIdNot(Long profileId, Long skillId, Long profileSkillId);
}
