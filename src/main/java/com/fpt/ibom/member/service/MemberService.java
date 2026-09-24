package com.fpt.ibom.member.service;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.common.PageResponse;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.master.entity.Language;
import com.fpt.ibom.master.entity.Seniority;
import com.fpt.ibom.master.entity.Skill;
import com.fpt.ibom.master.repository.LanguageRepository;
import com.fpt.ibom.master.repository.SeniorityRepository;
import com.fpt.ibom.master.repository.SkillRepository;
import com.fpt.ibom.member.dto.MatchingProfileResponse;
import com.fpt.ibom.member.dto.MemberSearchRequest;
import com.fpt.ibom.member.dto.MemberSearchResponse;
import com.fpt.ibom.member.dto.MemberSummaryResponse;
import com.fpt.ibom.member.repository.MemberSearchRepository;
import com.fpt.ibom.member.repository.MemberSearchRepository.LanguageCriteria;
import com.fpt.ibom.member.repository.MemberSearchRepository.MemberRow;
import com.fpt.ibom.member.repository.MemberSearchRepository.ProfileMatchRow;
import com.fpt.ibom.member.repository.MemberSearchRepository.SearchCriteria;
import com.fpt.ibom.member.repository.MemberSearchRepository.SkillCriteria;
import com.fpt.ibom.profile.entity.LanguageLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

	private final MemberSearchRepository searchRepository;
	private final SkillRepository skillRepository;
	private final SeniorityRepository seniorityRepository;
	private final LanguageRepository languageRepository;

	public MemberService(MemberSearchRepository searchRepository, SkillRepository skillRepository,
			SeniorityRepository seniorityRepository, LanguageRepository languageRepository) {
		this.searchRepository = searchRepository;
		this.skillRepository = skillRepository;
		this.seniorityRepository = seniorityRepository;
		this.languageRepository = languageRepository;
	}

	@Transactional(readOnly = true)
	public PageResponse<MemberSummaryResponse> list(int page, int size, String search, UserStatus status) {
		if (page < 0 || size <= 0) {
			throw validationError("Invalid pagination parameters");
		}
		SearchCriteria criteria = new SearchCriteria(normalizeSearch(search), status, List.of(), List.of());
		Page<MemberRow> members = findPage(page, size, criteria);
		if (members.getTotalElements() == 0) {
			return new PageResponse<>(List.of(), 0, size, 0, 0);
		}
		if (page >= members.getTotalPages()) {
			members = findPage(members.getTotalPages() - 1, size, criteria);
		}

		return new PageResponse<>(members.getContent().stream().map(this::toSummaryResponse).toList(), members.getNumber(),
				members.getSize(), members.getTotalElements(), members.getTotalPages());
	}

	@Transactional(readOnly = true)
	public PageResponse<MemberSearchResponse> search(MemberSearchRequest request) {
		ValidatedSearch validated = validateAndBuildCriteria(request);
		Page<MemberRow> members = findPage(validated.page(), validated.size(), validated.criteria());
		if (members.getTotalElements() == 0) {
			return new PageResponse<>(List.of(), 0, validated.size(), 0, 0);
		}
		if (validated.page() >= members.getTotalPages()) {
			members = findPage(members.getTotalPages() - 1, validated.size(), validated.criteria());
		}
		if (members.getContent().isEmpty()) {
			return new PageResponse<>(List.of(), members.getNumber(), members.getSize(), members.getTotalElements(),
					members.getTotalPages());
		}

		List<Long> memberIds = members.getContent().stream().map(MemberRow::id).toList();
		List<ProfileMatchRow> evidence = searchRepository.findMatchingProfiles(memberIds, validated.criteria());
		Map<Long, List<ProfileMatchRow>> evidenceByMember = evidence.stream()
				.collect(Collectors.groupingBy(ProfileMatchRow::memberId, LinkedHashMap::new, Collectors.toList()));
		List<MemberSearchResponse> content = members.getContent().stream()
				.map(member -> toSearchResponse(member, evidenceByMember.getOrDefault(member.id(), List.of()))).toList();
		return new PageResponse<>(content, members.getNumber(), members.getSize(), members.getTotalElements(),
				members.getTotalPages());
	}

	private Page<MemberRow> findPage(int page, int size, SearchCriteria criteria) {
		Pageable pageable = PageRequest.of(page, size);
		return searchRepository.findMembers(criteria, pageable);
	}

	private ValidatedSearch validateAndBuildCriteria(MemberSearchRequest request) {
		if (request == null) {
			throw validationError();
		}
		int page = request.page() == null ? 0 : request.page();
		int size = request.size() == null ? 10 : request.size();
		if (page < 0 || size <= 0) {
			throw validationError();
		}

		UserStatus status = parseStatus(request.status());
		List<MemberSearchRequest.SkillCondition> requestedSkills = request.skills() == null ? List.of() : request.skills();
		List<MemberSearchRequest.LanguageCondition> requestedLanguages = request.languages() == null
				? List.of() : request.languages();
		if (requestedSkills.stream().anyMatch(condition -> condition == null)
				|| requestedLanguages.stream().anyMatch(condition -> condition == null)) {
			throw validationError();
		}

		List<Long> skillIds = requestedSkills.stream().map(MemberSearchRequest.SkillCondition::skillId).toList();
		validateUniquePositive(skillIds);
		Map<Long, Skill> skillsById = loadSkills(skillIds);
		Set<Long> seniorityIds = requestedSkills.stream().map(MemberSearchRequest.SkillCondition::seniorityId)
				.filter(id -> id != null).collect(Collectors.toSet());
		if (seniorityIds.stream().anyMatch(id -> id <= 0)) {
			throw validationError();
		}
		Map<Long, Seniority> senioritiesById = loadSeniorities(seniorityIds);

		List<Long> languageIds = requestedLanguages.stream().map(MemberSearchRequest.LanguageCondition::languageId).toList();
		validateUniquePositive(languageIds);
		List<LanguageLevel> levels = requestedLanguages.stream()
				.map(condition -> parseLevel(condition.level())).toList();
		Map<Long, Language> languagesById = loadLanguages(languageIds);

		List<SkillCriteria> skills = requestedSkills.stream().map(condition -> {
			if (!skillsById.containsKey(condition.skillId())) {
				throw validationError();
			}
			Seniority seniority = condition.seniorityId() == null ? null : senioritiesById.get(condition.seniorityId());
			if (condition.seniorityId() != null && seniority == null) {
				throw validationError();
			}
			return new SkillCriteria(condition.skillId(), seniority == null ? null : seniority.getFromExperience(),
					seniority == null ? null : seniority.getToExperience());
		}).toList();
		List<LanguageCriteria> languages = requestedLanguages.stream().map(condition -> {
			int index = requestedLanguages.indexOf(condition);
			if (!languagesById.containsKey(condition.languageId())) {
				throw validationError();
			}
			return new LanguageCriteria(condition.languageId(), levels.get(index));
		}).toList();

		return new ValidatedSearch(new SearchCriteria(normalizeSearch(request.search()), status, skills, languages), page, size);
	}

	private Map<Long, Skill> loadSkills(List<Long> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		return skillRepository.findAllById(ids).stream().collect(Collectors.toMap(Skill::getId, skill -> skill));
	}

	private Map<Long, Seniority> loadSeniorities(Set<Long> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		return seniorityRepository.findAllById(ids.stream().toList()).stream()
				.collect(Collectors.toMap(Seniority::getId, seniority -> seniority));
	}

	private Map<Long, Language> loadLanguages(List<Long> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		return languageRepository.findAllById(ids).stream()
				.collect(Collectors.toMap(Language::getId, language -> language));
	}

	private void validateUniquePositive(List<Long> ids) {
		Set<Long> unique = new HashSet<>();
		for (Long id : ids) {
			if (id == null || id <= 0 || !unique.add(id)) {
				throw validationError();
			}
		}
	}

	private LanguageLevel parseLevel(String level) {
		if (level == null) {
			return null;
		}
		try {
			return LanguageLevel.valueOf(level.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			throw validationError();
		}
	}

	private UserStatus parseStatus(String status) {
		if (status == null) {
			return null;
		}
		try {
			return UserStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			throw validationError();
		}
	}

	private String normalizeSearch(String search) {
		if (search == null) {
			return null;
		}
		String normalized = search.trim();
		return normalized.isEmpty() ? null : normalized;
	}

	private MemberSummaryResponse toSummaryResponse(MemberRow member) {
		return new MemberSummaryResponse(member.id(), member.username(), member.email(), member.status(),
				member.activeProfileCount(), max(member.profileUpdatedAt(), member.accountUpdatedAt()));
	}

	private MemberSearchResponse toSearchResponse(MemberRow member, List<ProfileMatchRow> evidence) {
		Map<Long, ProfileMatchRow> profilesById = evidence.stream().collect(Collectors.toMap(ProfileMatchRow::profileId,
				row -> row, (first, ignored) -> first, LinkedHashMap::new));
		List<MatchingProfileResponse> matchingProfiles = profilesById.values().stream()
				.map(profile -> new MatchingProfileResponse(profile.profileId(), profile.profileName(), profile.firstName(),
						profile.lastName(), profile.jobTitle(), profile.updatedAt()))
				.toList();
		return new MemberSearchResponse(member.id(), member.username(), member.email(), member.status(),
				member.activeProfileCount(), max(member.profileUpdatedAt(), member.accountUpdatedAt()), matchingProfiles);
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
		return validationError("Validation failed");
	}

	private ApiException validationError(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, message);
	}

	private record ValidatedSearch(SearchCriteria criteria, int page, int size) {
	}
}
