package com.fpt.ibom.auth.repository;

import java.util.Optional;
import java.util.List;

import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.member.repository.MemberSummaryProjection;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
	Optional<UserAccount> findByEmailIgnoreCase(String email);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select user from UserAccount user where lower(user.email) = lower(:email)")
	Optional<UserAccount> findByEmailIgnoreCaseForUpdate(@Param("email") String email);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select user from UserAccount user where user.id = :id")
	Optional<UserAccount> findByIdForUpdate(@Param("id") Long id);

	boolean existsByEmailIgnoreCase(String email);
	boolean existsByUsernameIgnoreCase(String username);
	boolean existsByUsernameIgnoreCaseAndIdNot(String username, Long id);

	Optional<UserAccount> findByIdAndRole(Long id, UserRole role);

	@Query(value = """
			select user.id as id, user.username as username, user.email as email, user.status as status,
				count(case when profile.deletedAt is null then profile.id end) as activeProfileCount,
				user.updatedAt as accountUpdatedAt, max(profile.updatedAt) as profileUpdatedAt
			from UserAccount user
			left join Profile profile on profile.user = user
			where user.role = com.fpt.ibom.auth.entity.UserRole.MEMBER
			and (:status is null or user.status = :status)
			and (:search is null or lower(user.username) like lower(concat('%', :search, '%'))
				or lower(user.email) like lower(concat('%', :search, '%')))
			group by user.id, user.username, user.email, user.status, user.updatedAt
			order by case when user.status = com.fpt.ibom.auth.entity.UserStatus.ACTIVE then 0 else 1 end,
				lower(user.username), user.id
			""",
			countQuery = """
			select count(user)
			from UserAccount user
			where user.role = com.fpt.ibom.auth.entity.UserRole.MEMBER
			and (:status is null or user.status = :status)
			and (:search is null or lower(user.username) like lower(concat('%', :search, '%'))
				or lower(user.email) like lower(concat('%', :search, '%')))
			""")
	Page<MemberSummaryProjection> findMemberSummaries(@Param("status") UserStatus status,
			@Param("search") String search, Pageable pageable);

	@Query(value = """
			select user.id as id, user.username as username, user.email as email, user.status as status,
				count(case when profile.deletedAt is null then profile.id end) as activeProfileCount,
				user.updatedAt as accountUpdatedAt, max(profile.updatedAt) as profileUpdatedAt
			from UserAccount user
			left join Profile profile on profile.user = user
			where user.role = com.fpt.ibom.auth.entity.UserRole.MEMBER
			and (:status is null or user.status = :status)
			and exists (select matchingProfile.id from Profile matchingProfile
				where matchingProfile.user = user and matchingProfile.deletedAt is null
				and matchingProfile.id in :profileIds)
			group by user.id, user.username, user.email, user.status, user.updatedAt
			order by case when user.status = com.fpt.ibom.auth.entity.UserStatus.ACTIVE then 0 else 1 end,
				lower(user.username), user.id
			""",
			countQuery = """
			select count(user)
			from UserAccount user
			where user.role = com.fpt.ibom.auth.entity.UserRole.MEMBER
			and (:status is null or user.status = :status)
			and exists (select matchingProfile.id from Profile matchingProfile
				where matchingProfile.user = user and matchingProfile.deletedAt is null
				and matchingProfile.id in :profileIds)
			""")
	Page<MemberSummaryProjection> findMemberSummariesByProfileIds(@Param("status") UserStatus status,
			@Param("profileIds") List<Long> profileIds, Pageable pageable);
}
