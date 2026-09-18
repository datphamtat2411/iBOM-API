package com.fpt.ibom.member.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.common.PageResponse;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.master.entity.Seniority;
import com.fpt.ibom.master.entity.Skill;
import com.fpt.ibom.master.repository.SeniorityRepository;
import com.fpt.ibom.master.repository.SkillRepository;
import com.fpt.ibom.member.dto.MatchingProfileResponse;
import com.fpt.ibom.member.dto.MemberSkillSearchRequest;
import com.fpt.ibom.member.dto.MemberSkillSearchResponse;
import com.fpt.ibom.member.dto.MemberSummaryResponse;
import com.fpt.ibom.member.repository.MemberSkillSearchRepository;
import com.fpt.ibom.member.repository.MemberSummaryProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

	private final UserAccountRepository userAccountRepository;
	private final MemberSkillSearchRepository memberSkillSearchRepository;
	private final SkillRepository skillRepository;
	private final SeniorityRepository seniorityRepository;

	public MemberService(UserAccountRepository userAccountRepository) {
		this.userAccountRepository = userAccountRepository;
		this.memberSkillSearchRepository = null;
		this.skillRepository = null;
		this.seniorityRepository = null;
	}

	public MemberService(MemberSkillSearchRepository memberSkillSearchRepository, SkillRepository skillRepository,
			SeniorityRepository seniorityRepository) {
		this.userAccountRepository = null;
		this.memberSkillSearchRepository = memberSkillSearchRepository;
		this.skillRepository = skillRepository;
		this.seniorityRepository = seniorityRepository;
	}

	@Autowired
	public MemberService(UserAccountRepository userAccountRepository,
			MemberSkillSearchRepository memberSkillSearchRepository, SkillRepository skillRepository,
			SeniorityRepository seniorityRepository) {
		this.userAccountRepository = userAccountRepository;
		this.memberSkillSearchRepository = memberSkillSearchRepository;
		this.skillRepository = skillRepository;
		this.seniorityRepository = seniorityRepository;
	}

	@Transactional(readOnly = true)
	public PageResponse<MemberSummaryResponse> list(int page, int size, String search, UserStatus status) {
		if (page < 0 || size <= 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
					"Invalid pagination parameters");
		}

		String normalizedSearch = search == null ? null : search.trim();
		if (normalizedSearch != null && normalizedSearch.isEmpty()) {
			normalizedSearch = null;
		}
		Page<MemberSummaryProjection> members = findPage(page, size, normalizedSearch, status);
		if (members.getTotalElements() == 0) {
			return new PageResponse<>(java.util.List.of(), 0, size, 0, 0);
		}
		if (page >= members.getTotalPages()) {
			members = findPage(members.getTotalPages() - 1, size, normalizedSearch, status);
		}

		return new PageResponse<>(members.getContent().stream().map(this::toResponse).toList(), members.getNumber(),
				members.getSize(), members.getTotalElements(), members.getTotalPages());
	}

	@Transactional(readOnly = true)
	public PageResponse<MemberSkillSearchResponse> searchBySkill(MemberSkillSearchRequest request) {
		List<MemberSkillSearchRepository.Pair> pairs = validateAndBuildPairs(request);
		Pageable pageable = PageRequest.of(request.page(), request.size());
		Page<MemberSkillSearchRepository.MemberRow> members = memberSkillSearchRepository.findMembers(pairs,
				request.status(), pageable);
		if (members.getTotalElements() == 0) {
			return new PageResponse<>(List.of(), 0, request.size(), 0, 0);
		}
		if (request.page() >= members.getTotalPages()) {
			pageable = PageRequest.of(members.getTotalPages() - 1, request.size());
			members = memberSkillSearchRepository.findMembers(pairs, request.status(), pageable);
		}

		List<Long> memberIds = members.getContent().stream().map(MemberSkillSearchRepository.MemberRow::id).toList();
		List<MemberSkillSearchRepository.ProfileMatchRow> evidence = memberSkillSearchRepository
				.findMatchingProfiles(memberIds, pairs);
		Map<Long, List<MemberSkillSearchRepository.ProfileMatchRow>> evidenceByMember = evidence.stream()
				.collect(Collectors.groupingBy(MemberSkillSearchRepository.ProfileMatchRow::memberId, LinkedHashMap::new,
						Collectors.toList()));

		List<MemberSkillSearchResponse> content = members.getContent().stream().map(member ->
				toSearchResponse(member, evidenceByMember.getOrDefault(member.id(), List.of()))).toList();
		return new PageResponse<>(content, members.getNumber(), members.getSize(), members.getTotalElements(),
				members.getTotalPages());
	}

	private Page<MemberSummaryProjection> findPage(int page, int size, String search, UserStatus status) {
		Pageable pageable = PageRequest.of(page, size);
		return userAccountRepository.findMemberSummaries(status, search, pageable);
	}

	private MemberSummaryResponse toResponse(MemberSummaryProjection member) {
		Instant lastUpdatedAt = member.getAccountUpdatedAt();
		if (member.getProfileUpdatedAt() != null
				&& (lastUpdatedAt == null || member.getProfileUpdatedAt().isAfter(lastUpdatedAt))) {
			lastUpdatedAt = member.getProfileUpdatedAt();
		}
		return new MemberSummaryResponse(member.getId(), member.getUsername(), member.getEmail(), member.getStatus(),
				member.getActiveProfileCount(), lastUpdatedAt);
	}

	private List<MemberSkillSearchRepository.Pair> validateAndBuildPairs(MemberSkillSearchRequest request) {
		if (request == null || request.skillIds() == null || request.seniorityIds() == null
				|| request.skillIds().isEmpty() || request.seniorityIds().isEmpty()
				|| request.skillIds().size() != request.seniorityIds().size() || request.page() < 0 || request.size() <= 0) {
			throw validationError();
		}
		if (request.skillIds().stream().anyMatch(id -> id == null || id <= 0)
				|| request.seniorityIds().stream().anyMatch(id -> id == null || id <= 0)
				|| request.skillIds().stream().distinct().count() != request.skillIds().size()) {
			throw validationError();
		}

		List<Skill> skills = skillRepository.findAllById(request.skillIds());
		Set<Long> skillIds = skills.stream().map(Skill::getId).collect(Collectors.toSet());
		if (skillIds.size() != request.skillIds().size() || !skillIds.containsAll(request.skillIds())) {
			throw validationError();
		}
		List<Seniority> seniorities = seniorityRepository.findAllById(request.seniorityIds());
		Map<Long, Seniority> senioritiesById = seniorities.stream()
				.collect(Collectors.toMap(Seniority::getId, Function.identity()));
		if (senioritiesById.size() != request.seniorityIds().stream().distinct().count()
				|| !senioritiesById.keySet().containsAll(request.seniorityIds())) {
			throw validationError();
		}

		return request.skillIds().stream().map(skillId -> {
			int index = request.skillIds().indexOf(skillId);
			Long seniorityId = request.seniorityIds().get(index);
			Seniority seniority = senioritiesById.get(seniorityId);
			return new MemberSkillSearchRepository.Pair(skillId, seniorityId, seniority.getFromExperience(),
					seniority.getToExperience());
		}).toList();
	}

	private MemberSkillSearchResponse toSearchResponse(MemberSkillSearchRepository.MemberRow member,
			List<MemberSkillSearchRepository.ProfileMatchRow> evidence) {
		Map<Long, List<MemberSkillSearchRepository.ProfileMatchRow>> byProfile = evidence.stream()
				.collect(Collectors.groupingBy(MemberSkillSearchRepository.ProfileMatchRow::profileId, LinkedHashMap::new,
						Collectors.toList()));
		List<MatchingProfileResponse> profiles = byProfile.values().stream().map(profileEvidence -> {
			MemberSkillSearchRepository.ProfileMatchRow first = profileEvidence.get(0);
			return new MatchingProfileResponse(first.profileId(), first.profileName(), first.firstName(), first.lastName(),
					first.jobTitle(), first.updatedAt());
		}).toList();
		return new MemberSkillSearchResponse(member.id(), member.username(), member.email(), member.status(),
				member.activeProfileCount(), max(member.profileUpdatedAt(), member.accountUpdatedAt()), profiles);
	}

	private Instant max(Instant profileUpdatedAt, Instant accountUpdatedAt) {
		if (profileUpdatedAt == null) {
			return accountUpdatedAt;
		}
		if (accountUpdatedAt == null || profileUpdatedAt.isAfter(accountUpdatedAt)) {
			return profileUpdatedAt;
		}
		return accountUpdatedAt;
	}

	private ApiException validationError() {
		return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Validation failed");
	}
}
