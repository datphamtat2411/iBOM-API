package com.fpt.ibom.profile.repository;

import com.fpt.ibom.profile.entity.Profile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfileRepository extends JpaRepository<Profile, Long> {
	boolean existsByUserIdAndDeletedAtIsNullAndProfileNameIgnoreCase(Long userId, String profileName);

	boolean existsByUserIdAndDeletedAtIsNullAndProfileNameIgnoreCaseAndIdNot(Long userId, String profileName,
			Long id);

	List<Profile> findByUserIdAndDeletedAtIsNullOrderByUpdatedAtDescIdDesc(Long userId);

	Optional<Profile> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

	Optional<Profile> findByIdAndDeletedAtIsNull(Long id);

	long countByUserIdAndDeletedAtIsNull(Long userId);

	@Modifying(flushAutomatically = true)
	@Query("update Profile profile set profile.version = profile.version + 1, profile.hasPreviewed = false "
			+ "where profile.id = :profileId and profile.version = :expectedVersion")
	int advanceVersion(@Param("profileId") Long profileId, @Param("expectedVersion") long expectedVersion);

	@Modifying(flushAutomatically = true)
	@Query("update Profile profile set profile.hasPreviewed = true "
			+ "where profile.id = :profileId and profile.version = :expectedVersion")
	int markPreviewed(@Param("profileId") Long profileId, @Param("expectedVersion") long expectedVersion);
}
