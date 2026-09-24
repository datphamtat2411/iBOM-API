package com.fpt.ibom.auth.repository;

import java.util.Optional;
import java.util.List;

import com.fpt.ibom.user.repository.UserSummaryProjection;
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
	Optional<UserAccount> findByUsernameIgnoreCase(String username);

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
			select user.id as id, user.username as username, user.email as email,
				user.role as role, user.status as status
			from UserAccount user
			where (:roles is null or user.role in :roles)
			and (:search is null or lower(user.username) like lower(concat('%', :search, '%'))
				or lower(user.email) like lower(concat('%', :search, '%')))
			order by case when user.status = com.fpt.ibom.auth.entity.UserStatus.ACTIVE then 0 else 1 end,
				lower(user.username), user.id
			""",
			countQuery = """
			select count(user)
			from UserAccount user
			where (:roles is null or user.role in :roles)
			and (:search is null or lower(user.username) like lower(concat('%', :search, '%'))
				or lower(user.email) like lower(concat('%', :search, '%')))
			""")
	Page<UserSummaryProjection> findUserSummaries(@Param("roles") List<UserRole> roles,
			@Param("search") String search, Pageable pageable);
}
