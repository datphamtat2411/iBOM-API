package com.fpt.ibom.member.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.entity.ProfileSkill;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CommonAbstractCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public class MemberSkillSearchRepository {

	@PersistenceContext
	private EntityManager entityManager;

	public Page<MemberRow> findMembers(List<Pair> pairs, UserStatus status, Pageable pageable) {
		CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
		CriteriaQuery<Object[]> query = criteriaBuilder.createQuery(Object[].class);
		Root<com.fpt.ibom.auth.entity.UserAccount> user = query.from(com.fpt.ibom.auth.entity.UserAccount.class);

		Subquery<Long> activeProfileCount = query.subquery(Long.class);
		Root<Profile> activeProfile = activeProfileCount.from(Profile.class);
		activeProfileCount.select(criteriaBuilder.count(activeProfile));
		activeProfileCount.where(criteriaBuilder.equal(activeProfile.get("user"), user),
				criteriaBuilder.isNull(activeProfile.get("deletedAt")));

		Subquery<Instant> profileUpdatedAt = query.subquery(Instant.class);
		Root<Profile> profile = profileUpdatedAt.from(Profile.class);
		profileUpdatedAt.select(criteriaBuilder.greatest(profile.<Instant>get("updatedAt")));
		profileUpdatedAt.where(criteriaBuilder.equal(profile.get("user"), user));

		query.multiselect(user.get("id"), user.get("username"), user.get("email"), user.get("status"),
				activeProfileCount, user.get("updatedAt"), profileUpdatedAt);
		query.where(memberPredicate(criteriaBuilder, query, user, status, pairs));
		query.orderBy(criteriaBuilder.asc(criteriaBuilder.selectCase().when(
				criteriaBuilder.equal(user.get("status"), UserStatus.ACTIVE), 0).otherwise(1)),
				criteriaBuilder.asc(criteriaBuilder.lower(user.get("username"))), criteriaBuilder.asc(user.get("id")));

		List<Object[]> results = entityManager.createQuery(query).setFirstResult(Math.toIntExact(pageable.getOffset()))
				.setMaxResults(pageable.getPageSize()).getResultList();

		CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
		Root<com.fpt.ibom.auth.entity.UserAccount> countUser = countQuery.from(com.fpt.ibom.auth.entity.UserAccount.class);
		countQuery.select(criteriaBuilder.countDistinct(countUser));
		countQuery.where(memberPredicate(criteriaBuilder, countQuery, countUser, status, pairs));
		long total = entityManager.createQuery(countQuery).getSingleResult();

		List<MemberRow> members = results.stream().map(row -> new MemberRow((Long) row[0], (String) row[1],
				(String) row[2], (UserStatus) row[3], ((Number) row[4]).longValue(), (Instant) row[5], (Instant) row[6])).toList();
		return new PageImpl<>(members, pageable, total);
	}

	public List<ProfileMatchRow> findMatchingProfiles(List<Long> memberIds, List<Pair> pairs) {
		if (memberIds.isEmpty()) {
			return List.of();
		}

		CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
		CriteriaQuery<Object[]> query = criteriaBuilder.createQuery(Object[].class);
		Root<ProfileSkill> profileSkill = query.from(ProfileSkill.class);
		Join<ProfileSkill, Profile> profile = profileSkill.join("profile");
		Join<ProfileSkill, com.fpt.ibom.master.entity.Skill> skill = profileSkill.join("skill");

		List<Predicate> pairPredicates = new ArrayList<>();
		for (Pair pair : pairs) {
			pairPredicates.add(skillPairPredicate(criteriaBuilder, profileSkill, pair));
		}
		query.multiselect(profile.get("user").get("id"), profile.get("id"), profile.get("profileName"),
				profile.get("firstName"), profile.get("lastName"), profile.get("jobTitle"), profile.get("updatedAt"),
				skill.get("id"), profileSkill.get("experienceYears"));
		query.where(criteriaBuilder.and(criteriaBuilder.isNull(profile.get("deletedAt")),
				profile.get("user").get("id").in(memberIds),
				profileMatchesAll(criteriaBuilder, query, profile, pairs), criteriaBuilder.or(pairPredicates.toArray(Predicate[]::new))));
		query.orderBy(criteriaBuilder.desc(profile.get("updatedAt")), criteriaBuilder.desc(profile.get("id")),
				criteriaBuilder.asc(profileSkill.get("id")));

		return entityManager.createQuery(query).getResultList().stream().map(row -> new ProfileMatchRow((Long) row[0],
				(Long) row[1], (String) row[2], (String) row[3], (String) row[4], (String) row[5], (Instant) row[6],
				(Long) row[7], (BigDecimal) row[8])).toList();
	}

	private Predicate memberPredicate(CriteriaBuilder criteriaBuilder, CommonAbstractCriteria owner,
			Root<com.fpt.ibom.auth.entity.UserAccount> user, UserStatus status, List<Pair> pairs) {
		List<Predicate> predicates = new ArrayList<>();
		predicates.add(criteriaBuilder.equal(user.get("role"), UserRole.MEMBER));
		if (status != null) {
			predicates.add(criteriaBuilder.equal(user.get("status"), status));
		}

		Subquery<Long> matchingProfile = owner.subquery(Long.class);
		Root<Profile> profile = matchingProfile.from(Profile.class);
		matchingProfile.select(profile.get("id"));
		matchingProfile.where(criteriaBuilder.equal(profile.get("user"), user),
				criteriaBuilder.isNull(profile.get("deletedAt")),
				profileMatchesAll(criteriaBuilder, matchingProfile, profile, pairs));
		predicates.add(criteriaBuilder.exists(matchingProfile));
		return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
	}

	private Predicate profileMatchesAll(CriteriaBuilder criteriaBuilder, CommonAbstractCriteria owner,
			From<?, Profile> profile, List<Pair> pairs) {
		List<Predicate> predicates = new ArrayList<>();
		for (Pair pair : pairs) {
			Subquery<Long> matchingSkill = owner.subquery(Long.class);
			Root<ProfileSkill> profileSkill = matchingSkill.from(ProfileSkill.class);
			matchingSkill.select(profileSkill.get("id"));
			matchingSkill.where(criteriaBuilder.equal(profileSkill.get("profile"), profile),
					skillPairPredicate(criteriaBuilder, profileSkill, pair));
			predicates.add(criteriaBuilder.exists(matchingSkill));
		}
		return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
	}

	private Predicate skillPairPredicate(CriteriaBuilder criteriaBuilder, Root<ProfileSkill> profileSkill, Pair pair) {
		List<Predicate> predicates = new ArrayList<>();
		predicates.add(criteriaBuilder.equal(profileSkill.get("skill").get("id"), pair.skillId()));
		predicates.add(criteriaBuilder.greaterThanOrEqualTo(profileSkill.get("experienceYears"), pair.fromExperience()));
		if (pair.toExperience() != null) {
			predicates.add(criteriaBuilder.lessThan(profileSkill.get("experienceYears"), pair.toExperience()));
		}
		return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
	}

	public record Pair(Long skillId, Long seniorityId, BigDecimal fromExperience, BigDecimal toExperience) {
	}

	public record MemberRow(Long id, String username, String email, UserStatus status, Long activeProfileCount,
			Instant accountUpdatedAt, Instant profileUpdatedAt) {
	}

	public record ProfileMatchRow(Long memberId, Long profileId, String profileName, String firstName, String lastName,
			String jobTitle, Instant updatedAt, Long skillId, BigDecimal experienceYears) {
	}
}
