package com.fpt.ibom.member.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.profile.entity.LanguageLevel;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.entity.ProfileLanguage;
import com.fpt.ibom.profile.entity.ProfileSkill;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CommonAbstractCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public class MemberSearchRepository {

	@PersistenceContext
	private EntityManager entityManager;

	public Page<MemberRow> findMembers(SearchCriteria criteria, Pageable pageable) {
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
		query.where(memberPredicate(criteriaBuilder, query, user, criteria));
		query.orderBy(criteriaBuilder.asc(criteriaBuilder.selectCase().when(
				criteriaBuilder.equal(user.get("status"), UserStatus.ACTIVE), 0).otherwise(1)),
				criteriaBuilder.asc(criteriaBuilder.lower(user.get("username"))), criteriaBuilder.asc(user.get("id")));

		List<Object[]> results = entityManager.createQuery(query).setFirstResult(Math.toIntExact(pageable.getOffset()))
				.setMaxResults(pageable.getPageSize()).getResultList();

		CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
		Root<com.fpt.ibom.auth.entity.UserAccount> countUser = countQuery.from(com.fpt.ibom.auth.entity.UserAccount.class);
		countQuery.select(criteriaBuilder.countDistinct(countUser));
		countQuery.where(memberPredicate(criteriaBuilder, countQuery, countUser, criteria));
		long total = entityManager.createQuery(countQuery).getSingleResult();

		List<MemberRow> members = results.stream().map(row -> new MemberRow((Long) row[0], (String) row[1],
				(String) row[2], (UserStatus) row[3], ((Number) row[4]).longValue(), (Instant) row[5],
				(Instant) row[6])).toList();
		return new PageImpl<>(members, pageable, total);
	}

	public List<ProfileMatchRow> findMatchingProfiles(List<Long> memberIds, SearchCriteria criteria) {
		if (memberIds.isEmpty()) {
			return List.of();
		}

		CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
		CriteriaQuery<Object[]> query = criteriaBuilder.createQuery(Object[].class);
		Root<Profile> profile = query.from(Profile.class);
		query.multiselect(profile.get("user").get("id"), profile.get("id"), profile.get("profileName"),
				profile.get("firstName"), profile.get("lastName"), profile.get("jobTitle"), profile.get("updatedAt"));
		query.where(criteriaBuilder.and(criteriaBuilder.isNull(profile.get("deletedAt")),
				profile.get("user").get("id").in(memberIds), profileMatchesAll(criteriaBuilder, query, profile, criteria)));
		query.orderBy(criteriaBuilder.desc(profile.get("updatedAt")), criteriaBuilder.desc(profile.get("id")));

		return entityManager.createQuery(query).getResultList().stream().map(row -> new ProfileMatchRow((Long) row[0],
				(Long) row[1], (String) row[2], (String) row[3], (String) row[4], (String) row[5], (Instant) row[6])).toList();
	}

	private Predicate memberPredicate(CriteriaBuilder criteriaBuilder, CommonAbstractCriteria owner,
			Root<com.fpt.ibom.auth.entity.UserAccount> user, SearchCriteria criteria) {
		List<Predicate> predicates = new ArrayList<>();
		predicates.add(criteriaBuilder.equal(user.get("role"), UserRole.MEMBER));
		if (criteria.status() != null) {
			predicates.add(criteriaBuilder.equal(user.get("status"), criteria.status()));
		}
		if (criteria.search() != null) {
			predicates.add(criteriaBuilder.or(
					criteriaBuilder.like(criteriaBuilder.lower(user.get("username")),
							"%" + criteria.search().toLowerCase() + "%"),
					criteriaBuilder.like(criteriaBuilder.lower(user.get("email")),
							"%" + criteria.search().toLowerCase() + "%")));
		}

		if (criteria.hasProfileConditions()) {
			Subquery<Long> matchingProfile = owner.subquery(Long.class);
			Root<Profile> profile = matchingProfile.from(Profile.class);
			matchingProfile.select(profile.get("id"));
			matchingProfile.where(criteriaBuilder.equal(profile.get("user"), user),
					criteriaBuilder.isNull(profile.get("deletedAt")),
					profileMatchesAll(criteriaBuilder, matchingProfile, profile, criteria));
			predicates.add(criteriaBuilder.exists(matchingProfile));
		}
		return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
	}

	private Predicate profileMatchesAll(CriteriaBuilder criteriaBuilder, CommonAbstractCriteria owner,
			From<?, Profile> profile, SearchCriteria criteria) {
		List<Predicate> predicates = new ArrayList<>();
		for (SkillCriteria skill : criteria.skills()) {
			Subquery<Long> matchingSkill = owner.subquery(Long.class);
			Root<ProfileSkill> profileSkill = matchingSkill.from(ProfileSkill.class);
			matchingSkill.select(profileSkill.get("id"));
			matchingSkill.where(criteriaBuilder.equal(profileSkill.get("profile"), profile),
					skillPredicate(criteriaBuilder, profileSkill, skill));
			predicates.add(criteriaBuilder.exists(matchingSkill));
		}
		for (LanguageCriteria language : criteria.languages()) {
			Subquery<Long> matchingLanguage = owner.subquery(Long.class);
			Root<ProfileLanguage> profileLanguage = matchingLanguage.from(ProfileLanguage.class);
			matchingLanguage.select(profileLanguage.get("id"));
			List<Predicate> languagePredicates = new ArrayList<>();
			languagePredicates.add(criteriaBuilder.equal(profileLanguage.get("profile"), profile));
			languagePredicates.add(criteriaBuilder.equal(profileLanguage.get("language").get("id"), language.languageId()));
			if (language.level() != null) {
				languagePredicates.add(criteriaBuilder.equal(profileLanguage.get("level"), language.level()));
			}
			matchingLanguage.where(languagePredicates.toArray(Predicate[]::new));
			predicates.add(criteriaBuilder.exists(matchingLanguage));
		}
		return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
	}

	private Predicate skillPredicate(CriteriaBuilder criteriaBuilder, Root<ProfileSkill> profileSkill,
			SkillCriteria skill) {
		List<Predicate> predicates = new ArrayList<>();
		predicates.add(criteriaBuilder.equal(profileSkill.get("skill").get("id"), skill.skillId()));
		if (skill.fromExperience() != null) {
			predicates.add(criteriaBuilder.greaterThanOrEqualTo(profileSkill.get("experienceYears"), skill.fromExperience()));
		}
		if (skill.toExperience() != null) {
			predicates.add(criteriaBuilder.lessThan(profileSkill.get("experienceYears"), skill.toExperience()));
		}
		return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
	}

	public record SearchCriteria(String search, UserStatus status, List<SkillCriteria> skills,
			List<LanguageCriteria> languages) {
		public boolean hasProfileConditions() {
			return !skills.isEmpty() || !languages.isEmpty();
		}
	}

	public record SkillCriteria(Long skillId, BigDecimal fromExperience, BigDecimal toExperience) {
	}

	public record LanguageCriteria(Long languageId, LanguageLevel level) {
	}

	public record MemberRow(Long id, String username, String email, UserStatus status, Long activeProfileCount,
			Instant accountUpdatedAt, Instant profileUpdatedAt) {
	}

	public record ProfileMatchRow(Long memberId, Long profileId, String profileName, String firstName, String lastName,
			String jobTitle, Instant updatedAt) {
	}
}
