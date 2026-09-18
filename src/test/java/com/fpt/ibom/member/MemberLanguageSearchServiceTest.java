package com.fpt.ibom.member;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.common.PageResponse;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.master.entity.Language;
import com.fpt.ibom.master.repository.LanguageRepository;
import com.fpt.ibom.member.dto.MatchingProfileResponse;
import com.fpt.ibom.member.dto.MemberLanguageSearchResponse;
import com.fpt.ibom.member.repository.MemberLanguageSearchRepository;
import com.fpt.ibom.member.service.MemberLanguageSearchService;
import com.fpt.ibom.profile.entity.LanguageLevel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class MemberLanguageSearchServiceTest {

	private final MemberLanguageSearchRepository searchRepository = Mockito.mock(MemberLanguageSearchRepository.class);
	private final LanguageRepository languageRepository = Mockito.mock(LanguageRepository.class);
	private final MemberLanguageSearchService service = new MemberLanguageSearchService(searchRepository,
			languageRepository);

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
		verifyNoInteractions(languageRepository, searchRepository);
	}

	@Test
	void rejectsLanguageIdsMissingFromMasterDataAsValidationError() {
		when(languageRepository.findById(99L)).thenReturn(Optional.empty());

		ApiException exception = assertThrows(ApiException.class,
				() -> service.search(List.of(99L), List.of("NATIVE"), 0, 10, null));

		assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
		verifyNoInteractions(searchRepository);
	}

	@Test
	void forwardsExactPairsAndMapsOnlyMatchingProfileEvidenceForPageMembers() {
		when(languageRepository.findById(1L)).thenReturn(Optional.of(language(1L, "English")));
		when(languageRepository.findById(2L)).thenReturn(Optional.of(language(2L, "Vietnamese")));
		MemberLanguageSearchRepository.MemberRow member = new MemberLanguageSearchRepository.MemberRow(7L, "Alice",
				"alice@example.com", UserStatus.INACTIVE, 2L, Instant.parse("2026-01-02T00:00:00Z"),
				Instant.parse("2026-01-04T00:00:00Z"));
		when(searchRepository.findMembers(any(), eq(UserStatus.INACTIVE), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(member), PageRequest.of(0, 10), 1));
		when(searchRepository.findMatchingProfiles(eq(List.of(7L)), any())).thenReturn(List.of(
				new MemberLanguageSearchRepository.ProfileMatchRow(7L, 11L, "Primary", "A", "One", "Engineer",
						Instant.parse("2026-01-03T00:00:00Z")),
				new MemberLanguageSearchRepository.ProfileMatchRow(7L, 12L, "Secondary", "B", "Two", "Lead",
						Instant.parse("2026-01-02T00:00:00Z"))));

		PageResponse<MemberLanguageSearchResponse> result = service.search(List.of(1L, 2L),
				List.of(" advanced ", "NATIVE"), 0, 10, UserStatus.INACTIVE);

		assertEquals(1, result.totalElements());
		assertEquals(UserStatus.INACTIVE, result.content().get(0).status());
		assertEquals(List.of(11L, 12L), result.content().get(0).matchingProfiles().stream()
				.map(MatchingProfileResponse::id).toList());
		assertEquals("Primary", result.content().get(0).matchingProfiles().get(0).profileName());
		ArgumentCaptor<List<MemberLanguageSearchRepository.Pair>> pairs = ArgumentCaptor.forClass(List.class);
		verify(searchRepository).findMembers(pairs.capture(), eq(UserStatus.INACTIVE), any(Pageable.class));
		assertEquals(List.of(1L, 2L), pairs.getValue().stream()
				.map(MemberLanguageSearchRepository.Pair::languageId).toList());
		assertEquals(List.of(LanguageLevel.ADVANCED, LanguageLevel.NATIVE), pairs.getValue().stream()
				.map(MemberLanguageSearchRepository.Pair::level).toList());
		verify(searchRepository).findMatchingProfiles(eq(List.of(7L)), eq(pairs.getValue()));
	}

	@Test
	void doesNotLoadEvidenceWhenDatabaseFindsNoMembersAndForwardsStatusAndPagination() {
		when(languageRepository.findById(1L)).thenReturn(Optional.of(language(1L, "English")));
		when(languageRepository.findById(2L)).thenReturn(Optional.of(language(2L, "Vietnamese")));
		when(searchRepository.findMembers(any(), eq(UserStatus.INACTIVE), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(), PageRequest.of(3, 2), 0));

		PageResponse<MemberLanguageSearchResponse> result = service.search(List.of(1L, 2L),
				List.of("ADVANCED", "NATIVE"), 3, 2, UserStatus.INACTIVE);

		assertEquals(List.of(), result.content());
		verify(searchRepository, never()).findMatchingProfiles(any(), any());
		verify(searchRepository).findMembers(any(), eq(UserStatus.INACTIVE), eq(PageRequest.of(3, 2)));
	}

	@Test
	void recoversOutOfRangePageBeforeLoadingEvidence() {
		when(languageRepository.findById(1L)).thenReturn(Optional.of(language(1L, "English")));
		MemberLanguageSearchRepository.MemberRow member = new MemberLanguageSearchRepository.MemberRow(7L, "Alice",
				"alice@example.com", UserStatus.ACTIVE, 1L, Instant.parse("2026-01-01T00:00:00Z"), null);
		when(searchRepository.findMembers(any(), eq(null), any(Pageable.class))).thenReturn(
				new PageImpl<>(List.of(), PageRequest.of(4, 2), 5),
				new PageImpl<>(List.of(member), PageRequest.of(2, 2), 5));
		when(searchRepository.findMatchingProfiles(eq(List.of(7L)), any())).thenReturn(List.of(
				new MemberLanguageSearchRepository.ProfileMatchRow(7L, 11L, "CV", "A", "One", "Engineer", null)));

		PageResponse<MemberLanguageSearchResponse> result = service.search(List.of(1L), List.of("NATIVE"), 4, 2, null);

		assertEquals(2, result.page());
		assertEquals(3, result.totalPages());
		assertEquals("Alice", result.content().get(0).username());
		verify(searchRepository, Mockito.times(2)).findMembers(any(), eq(null), any(Pageable.class));
		verify(searchRepository).findMatchingProfiles(eq(List.of(7L)), any());
	}

	private void assertValidation(List<Long> languageIds, List<String> levels) {
		ApiException exception = assertThrows(ApiException.class,
				() -> service.search(languageIds, levels, 0, 10, null));
		assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
	}

	private Language language(Long id, String name) {
		Language language = new Language(name);
		org.springframework.test.util.ReflectionTestUtils.setField(language, "id", id);
		return language;
	}
}
