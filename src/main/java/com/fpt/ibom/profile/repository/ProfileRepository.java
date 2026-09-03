package com.fpt.ibom.profile.repository;

import com.fpt.ibom.profile.entity.Profile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileRepository extends JpaRepository<Profile, Long> {
	boolean existsByUserIdAndDeletedAtIsNullAndProfileNameIgnoreCase(Long userId, String profileName);

	boolean existsByUserIdAndDeletedAtIsNullAndProfileNameIgnoreCaseAndIdNot(Long userId, String profileName,
			Long id);

	List<Profile> findByUserIdAndDeletedAtIsNullOrderByUpdatedAtDescIdDesc(Long userId);

	Optional<Profile> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);
}
