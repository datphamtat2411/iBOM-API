package com.fpt.ibom.member.service;

import java.time.Instant;
import java.util.ArrayList;
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
import com.fpt.ibom.master.repository.LanguageRepository;
import com.fpt.ibom.member.dto.MatchingProfileResponse;
import com.fpt.ibom.member.dto.MemberLanguageSearchResponse;
import com.fpt.ibom.member.repository.MemberLanguageSearchRepository;
import com.fpt.ibom.profile.entity.LanguageLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberLanguageSearchService {

	private final MemberLanguageSearchRepository searchRepository;
	private final LanguageRepository languageRepository;

	public MemberLanguageSearchService(MemberLanguageSearchRepository searchRepository,
			LanguageRepository languageRepository) {
		this.searchRepository = searchRepository;
		this.languageRepository = languageRepository;
	}

	@Transactional(readOnly = true)
	public PageResponse<MemberLanguageSearchResponse> search(List<Long> languageIds, List<String> levels, int page,
			int size, UserStatus status) {
		List<LanguageLevel> requestedLevels = validate(languageIds, levels, page, size);
		for (Long languageId : languageIds) {
			if (languageRepository.findById(languageId).isEmpty()) {
				throw validationError();
			}
		}

		List<MemberLanguageSearchRepository.Pair> pairs = new ArrayList<>();
		for (int index = 0; index < languageIds.size(); index++) {
			pairs.add(new MemberLanguageSearchRepository.Pair(languageIds.get(index), requestedLevels.get(index)));
		}

		Page<MemberLanguageSearchRepository.MemberRow> members = findPage(page, size, status, pairs);
		if (members.getTotalElements() == 0) {
			return emptyPage(size);
		}
		if (page >= members.getTotalPages()) {
			members = findPage(members.getTotalPages() - 1, size, status, pairs);
		}

		List<Long> memberIds = members.getContent().stream().map(MemberLanguageSearchRepository.MemberRow::id).toList();
		List<MemberLanguageSearchRepository.ProfileMatchRow> evidence = searchRepository
				.findMatchingProfiles(memberIds, pairs);
		Map<Long, List<MemberLanguageSearchRepository.ProfileMatchRow>> evidenceByMember = evidence.stream()
				.collect(Collectors.groupingBy(MemberLanguageSearchRepository.ProfileMatchRow::memberId,
						LinkedHashMap::new, Collectors.toList()));

		List<MemberLanguageSearchResponse> content = members.getContent().stream()
				.map(member -> toResponse(member, evidenceByMember.getOrDefault(member.id(), List.of()))).toList();
		return new PageResponse<>(content, members.getNumber(), members.getSize(), members.getTotalElements(),
				members.getTotalPages());
	}

	private List<LanguageLevel> validate(List<Long> languageIds, List<String> levels, int page, int size) {
		if (page < 0 || size <= 0 || languageIds == null || levels == null || languageIds.isEmpty()
				|| levels.isEmpty() || languageIds.size() != levels.size()) {
			throw validationError();
		}
		Set<Long> uniqueLanguageIds = new HashSet<>();
		List<LanguageLevel> parsedLevels = new ArrayList<>();
		for (int index = 0; index < languageIds.size(); index++) {
			Long languageId = languageIds.get(index);
			if (languageId == null || languageId <= 0 || !uniqueLanguageIds.add(languageId)) {
				throw validationError();
			}
			try {
				parsedLevels.add(LanguageLevel.valueOf(levels.get(index).trim().toUpperCase(Locale.ROOT)));
			} catch (IllegalArgumentException | NullPointerException exception) {
				throw validationError();
			}
		}
		return parsedLevels;
	}

	private Page<MemberLanguageSearchRepository.MemberRow> findPage(int page, int size, UserStatus status,
			List<MemberLanguageSearchRepository.Pair> pairs) {
		Pageable pageable = PageRequest.of(page, size);
		return searchRepository.findMembers(pairs, status, pageable);
	}

	private MemberLanguageSearchResponse toResponse(MemberLanguageSearchRepository.MemberRow member,
			List<MemberLanguageSearchRepository.ProfileMatchRow> evidence) {
		Map<Long, MemberLanguageSearchRepository.ProfileMatchRow> profilesById = evidence.stream().collect(Collectors.toMap(
				MemberLanguageSearchRepository.ProfileMatchRow::profileId, row -> row, (first, ignored) -> first,
				LinkedHashMap::new));
		List<MatchingProfileResponse> matchingProfiles = profilesById.values().stream()
				.map(profile -> new MatchingProfileResponse(profile.profileId(), profile.profileName(), profile.firstName(),
						profile.lastName(), profile.jobTitle(), profile.updatedAt()))
				.toList();
		return new MemberLanguageSearchResponse(member.id(), member.username(), member.email(), member.status(),
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

	private PageResponse<MemberLanguageSearchResponse> emptyPage(int size) {
		return new PageResponse<>(List.of(), 0, size, 0, 0);
	}

	private ApiException validationError() {
		return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Validation failed");
	}
}
