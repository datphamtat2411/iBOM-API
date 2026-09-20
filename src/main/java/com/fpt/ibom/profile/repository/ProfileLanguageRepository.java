package com.fpt.ibom.profile.repository;

import java.util.List;
import java.util.Optional;

import com.fpt.ibom.profile.entity.ProfileLanguage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfileLanguageRepository extends JpaRepository<ProfileLanguage, Long> {

	@EntityGraph(attributePaths = "language")
	List<ProfileLanguage> findByProfileId(Long profileId);

	boolean existsByProfileId(Long profileId);

	@Query("select profileLanguage.profile.id from ProfileLanguage profileLanguage "
			+ "where profileLanguage.profile.id in :profileIds")
	List<Long> findProfileIdsByProfileIdIn(@Param("profileIds") List<Long> profileIds);

	Optional<ProfileLanguage> findByIdAndProfileId(Long profileLanguageId, Long profileId);

	boolean existsByProfileIdAndLanguageId(Long profileId, Long languageId);

	boolean existsByProfileIdAndLanguageIdAndIdNot(Long profileId, Long languageId, Long profileLanguageId);

	boolean existsByLanguageId(Long languageId);

	@EntityGraph(attributePaths = { "profile", "language" })
	@Query("select profileLanguage from ProfileLanguage profileLanguage "
			+ "where profileLanguage.profile.deletedAt is null "
			+ "and profileLanguage.language.id in :languageIds")
	List<ProfileLanguage> findActiveByLanguageIds(@Param("languageIds") List<Long> languageIds);
}
