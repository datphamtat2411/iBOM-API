package com.fpt.ibom.member;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.common.PageResponse;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.master.entity.Language;
import com.fpt.ibom.master.repository.LanguageRepository;
import com.fpt.ibom.member.dto.MemberLanguageSearchResponse;
import com.fpt.ibom.member.service.MemberLanguageSearchService;
import com.fpt.ibom.profile.entity.LanguageLevel;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.entity.ProfileLanguage;
import com.fpt.ibom.profile.repository.ProfileLanguageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class MemberLanguageSearchServiceTest {

	private final UserAccountRepository userAccountRepository = Mockito.mock(UserAccountRepository.class);
	private final ProfileLanguageRepository profileLanguageRepository = Mockito.mock(ProfileLanguageRepository.class);
	private final LanguageRepository languageRepository = Mockito.mock(LanguageRepository.class);
	private final MemberLanguageSearchService service = new MemberLanguageSearchService(userAccountRepository,
			profileLanguageRepository, languageRepository);

	@Test
	void rejectsMissingEmptyUnequalDuplicateAndInvalidPairInputsBeforeSearching() {
		assertValidation(null, List.of("NATIVE"));
		assertValidation(List.of(), List.of("NATIVE"));
		assertValidation(List.of(1L), null);
		assertValidation(List.of(1L), List.of());
		assertValidation(List.of(1L), List.of("NATIVE", "ADVANCED"));
		assertValidation(List.of(1L, 1L), List.of("NATIVE", "ADVANCED"));
		assertValidation(List.of(0L), List.of("NATIVE"));
		assertValidation(List.of(1L), List.of(" "));
		assertValidation(List.of(1L), List.of("FLUENT"));
		verifyNoInteractions(languageRepository, profileLanguageRepository, userAccountRepository);
	}

	@Test
	void rejectsLanguageIdsMissingFromMasterDataAsValidationError() {
		when(languageRepository.findById(99L)).thenReturn(Optional.empty());

		ApiException exception = assertThrows(ApiException.class,
				() -> service.search(List.of(99L), List.of("NATIVE"), 0, 10, null));

		assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
		verifyNoInteractions(profileLanguageRepository, userAccountRepository);
	}

	@Test
	void requiresAllExactIndexedPairsOnOneActiveProfileAndGroupsEvidenceByMember() {
		UserAccount member = user(7L, "Alice");
		Language english = language(1L, "English");
		Language vietnamese = language(2L, "Vietnamese");
		when(languageRepository.findById(1L)).thenReturn(Optional.of(english));
		when(languageRepository.findById(2L)).thenReturn(Optional.of(vietnamese));

		Profile first = profile(11L, member, "Primary");
		Profile second = profile(12L, member, "Secondary");
		List<ProfileLanguage> associations = List.of(
				profileLanguage(101L, first, english, LanguageLevel.ADVANCED),
				profileLanguage(102L, first, vietnamese, LanguageLevel.NATIVE),
				profileLanguage(103L, second, english, LanguageLevel.ADVANCED),
				profileLanguage(104L, second, vietnamese, LanguageLevel.NATIVE));
		when(profileLanguageRepository.findActiveByLanguageIds(List.of(1L, 2L))).thenReturn(associations);
		com.fpt.ibom.member.repository.MemberSummaryProjection memberProjection = projection(member);
		when(userAccountRepository.findMemberSummariesByProfileIds(eq(null), eq(List.of(11L, 12L)), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(memberProjection), org.springframework.data.domain.PageRequest.of(0, 10), 1));

		PageResponse<MemberLanguageSearchResponse> result = service.search(List.of(1L, 2L),
				List.of(" advanced ", "NATIVE"), 0, 10, null);

		assertEquals(1, result.totalElements());
		assertEquals(1, result.content().size());
		assertEquals(2, result.content().get(0).matchingProfiles().size());
		assertEquals(List.of("Vietnamese", "English"), result.content().get(0).matchingProfiles().get(0)
				.matchingLanguages().stream().map(languageResponse -> languageResponse.languageName()).toList());
		verify(userAccountRepository).findMemberSummariesByProfileIds(eq(null), eq(List.of(11L, 12L)), any(Pageable.class));
	}

	@Test
	void neverCombinesPairsAcrossProfilesAndForwardsStatusAndPagination() {
		UserAccount member = user(8L, "Bob");
		Language english = language(1L, "English");
		Language vietnamese = language(2L, "Vietnamese");
		when(languageRepository.findById(1L)).thenReturn(Optional.of(english));
		when(languageRepository.findById(2L)).thenReturn(Optional.of(vietnamese));
		Profile first = profile(21L, member, "English only");
		Profile second = profile(22L, member, "Vietnamese only");
		when(profileLanguageRepository.findActiveByLanguageIds(List.of(1L, 2L))).thenReturn(List.of(
				profileLanguage(201L, first, english, LanguageLevel.ADVANCED),
				profileLanguage(202L, second, vietnamese, LanguageLevel.NATIVE)));

		PageResponse<MemberLanguageSearchResponse> result = service.search(List.of(1L, 2L),
				List.of("ADVANCED", "NATIVE"), 3, 2, UserStatus.INACTIVE);

		assertEquals(List.of(), result.content());
		verify(userAccountRepository, never()).findMemberSummariesByProfileIds(any(), any(), any());
	}

	private void assertValidation(List<Long> languageIds, List<String> levels) {
		ApiException exception = assertThrows(ApiException.class,
				() -> service.search(languageIds, levels, 0, 10, null));
		assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
	}

	private UserAccount user(Long id, String username) {
		UserAccount user = new UserAccount(username.toLowerCase() + "@example.com", username, "hash", UserRole.MEMBER,
				UserStatus.ACTIVE);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	private Profile profile(Long id, UserAccount user, String name) {
		Profile profile = new Profile(user, name, "First", "Last", "Engineer", BigDecimal.ONE, "Personality", "Summary");
		ReflectionTestUtils.setField(profile, "id", id);
		ReflectionTestUtils.setField(profile, "updatedAt", Instant.parse("2026-01-01T00:00:00Z"));
		return profile;
	}

	private Language language(Long id, String name) {
		Language language = new Language(name);
		ReflectionTestUtils.setField(language, "id", id);
		return language;
	}

	private ProfileLanguage profileLanguage(Long id, Profile profile, Language language, LanguageLevel level) {
		ProfileLanguage profileLanguage = new ProfileLanguage(profile, language, level);
		ReflectionTestUtils.setField(profileLanguage, "id", id);
		return profileLanguage;
	}

	private com.fpt.ibom.member.repository.MemberSummaryProjection projection(UserAccount user) {
		com.fpt.ibom.member.repository.MemberSummaryProjection projection = Mockito
				.mock(com.fpt.ibom.member.repository.MemberSummaryProjection.class);
		when(projection.getId()).thenReturn(user.getId());
		when(projection.getUsername()).thenReturn(user.getUsername());
		when(projection.getEmail()).thenReturn(user.getEmail());
		when(projection.getStatus()).thenReturn(user.getStatus());
		when(projection.getActiveProfileCount()).thenReturn(2L);
		when(projection.getAccountUpdatedAt()).thenReturn(Instant.parse("2026-01-02T00:00:00Z"));
		when(projection.getProfileUpdatedAt()).thenReturn(Instant.parse("2026-01-03T00:00:00Z"));
		return projection;
	}
}
