package com.fpt.ibom.profile.repository;

import java.util.List;
import java.util.Optional;

import com.fpt.ibom.profile.entity.ProfileLanguage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileLanguageRepository extends JpaRepository<ProfileLanguage, Long> {

	@EntityGraph(attributePaths = "language")
	List<ProfileLanguage> findByProfileId(Long profileId);

	Optional<ProfileLanguage> findByIdAndProfileId(Long profileLanguageId, Long profileId);

	boolean existsByProfileIdAndLanguageId(Long profileId, Long languageId);

	boolean existsByProfileIdAndLanguageIdAndIdNot(Long profileId, Long languageId, Long profileLanguageId);
}
