package com.fpt.ibom.member.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.common.PageResponse;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.master.repository.LanguageRepository;
import com.fpt.ibom.member.dto.MemberLanguageSearchProfileResponse;
import com.fpt.ibom.member.dto.MemberLanguageSearchResponse;
import com.fpt.ibom.member.repository.MemberSummaryProjection;
import com.fpt.ibom.profile.dto.ProfileLanguageResponse;
import com.fpt.ibom.profile.entity.LanguageLevel;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.entity.ProfileLanguage;
import com.fpt.ibom.profile.repository.ProfileLanguageRepository;
import com.fpt.ibom.profile.service.ProfileDisplayOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberLanguageSearchService {

	private final UserAccountRepository userAccountRepository;
	private final ProfileLanguageRepository profileLanguageRepository;
	private final LanguageRepository languageRepository;

	public MemberLanguageSearchService(UserAccountRepository userAccountRepository,
			ProfileLanguageRepository profileLanguageRepository, LanguageRepository languageRepository) {
		this.userAccountRepository = userAccountRepository;
		this.profileLanguageRepository = profileLanguageRepository;
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

		List<ProfileLanguage> associations = profileLanguageRepository.findActiveByLanguageIds(languageIds);
		Map<Long, Profile> profiles = new HashMap<>();
		Map<Long, List<ProfileLanguage>> matchingLanguages = new HashMap<>();
		for (ProfileLanguage association : associations) {
			Profile profile = association.getProfile();
			profiles.putIfAbsent(profile.getId(), profile);
			int pairIndex = languageIds.indexOf(association.getLanguage().getId());
			if (pairIndex >= 0 && association.getLevel() == requestedLevels.get(pairIndex)) {
				matchingLanguages.computeIfAbsent(profile.getId(), ignored -> new java.util.ArrayList<>()).add(association);
			}
		}

		Set<Long> matchingProfileIds = new HashSet<>();
		for (Map.Entry<Long, List<ProfileLanguage>> entry : matchingLanguages.entrySet()) {
			if (entry.getValue().size() == languageIds.size()) {
				matchingProfileIds.add(entry.getKey());
			}
		}
		if (matchingProfileIds.isEmpty()) {
			return emptyPage(size);
		}

		Page<MemberSummaryProjection> members = findPage(page, size, status, matchingProfileIds);
		if (members.getTotalElements() == 0) {
			return emptyPage(size);
		}
		if (page >= members.getTotalPages()) {
			members = findPage(members.getTotalPages() - 1, size, status, matchingProfileIds);
		}

		Map<Long, List<ProfileLanguage>> evidenceByProfile = new HashMap<>();
		for (Long profileId : matchingProfileIds) {
			if (matchingLanguages.containsKey(profileId)) {
				evidenceByProfile.put(profileId, matchingLanguages.get(profileId));
			}
		}
		return new PageResponse<>(members.getContent().stream()
				.map(member -> toResponse(member, profiles, evidenceByProfile)).toList(), members.getNumber(), members.getSize(),
				members.getTotalElements(), members.getTotalPages());
	}

	private List<LanguageLevel> validate(List<Long> languageIds, List<String> levels, int page, int size) {
		if (page < 0 || size <= 0 || languageIds == null || levels == null || languageIds.isEmpty()
				|| levels.isEmpty() || languageIds.size() != levels.size()) {
			throw validationError();
		}
		Set<Long> uniqueLanguageIds = new HashSet<>();
		List<LanguageLevel> parsedLevels = new java.util.ArrayList<>();
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

	private Page<MemberSummaryProjection> findPage(int page, int size, UserStatus status, Set<Long> profileIds) {
		Pageable pageable = PageRequest.of(page, size);
		return userAccountRepository.findMemberSummariesByProfileIds(status, profileIds.stream().sorted().toList(), pageable);
	}

	private MemberLanguageSearchResponse toResponse(MemberSummaryProjection member, Map<Long, Profile> profiles,
			Map<Long, List<ProfileLanguage>> evidenceByProfile) {
		Instant lastUpdatedAt = member.getAccountUpdatedAt();
		if (member.getProfileUpdatedAt() != null
				&& (lastUpdatedAt == null || member.getProfileUpdatedAt().isAfter(lastUpdatedAt))) {
			lastUpdatedAt = member.getProfileUpdatedAt();
		}
		List<MemberLanguageSearchProfileResponse> matchingProfiles = profiles.values().stream()
				.filter(profile -> profile.getUser().getId().equals(member.getId()) && evidenceByProfile.containsKey(profile.getId()))
				.sorted((first, second) -> {
					int updatedOrder = java.util.Comparator.comparing(Profile::getUpdatedAt,
							java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())).compare(first, second);
					return updatedOrder != 0 ? updatedOrder : Long.compare(second.getId(), first.getId());
				})
				.map(profile -> new MemberLanguageSearchProfileResponse(profile.getId(), profile.getProfileName(),
						profile.getFirstName(), profile.getLastName(), profile.getJobTitle(), profile.getUpdatedAt(),
						evidenceByProfile.get(profile.getId()).stream().sorted(ProfileDisplayOrder.languageComparator())
								.map(ProfileLanguageResponse::from).toList()))
				.toList();
		return new MemberLanguageSearchResponse(member.getId(), member.getUsername(), member.getEmail(), member.getStatus(),
				member.getActiveProfileCount(), lastUpdatedAt, matchingProfiles);
	}

	private PageResponse<MemberLanguageSearchResponse> emptyPage(int size) {
		return new PageResponse<>(List.of(), 0, size, 0, 0);
	}

	private ApiException validationError() {
		return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Validation failed");
	}
}
