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

import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.common.PageResponse;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.master.entity.Language;
import com.fpt.ibom.master.entity.Seniority;
import com.fpt.ibom.master.entity.Skill;
import com.fpt.ibom.master.repository.LanguageRepository;
import com.fpt.ibom.master.repository.SeniorityRepository;
import com.fpt.ibom.master.repository.SkillRepository;
import com.fpt.ibom.member.dto.MatchingProfileResponse;
import com.fpt.ibom.member.dto.MemberSearchRequest;
import com.fpt.ibom.member.dto.MemberSearchResponse;
import com.fpt.ibom.member.repository.MemberSearchRepository;
import com.fpt.ibom.member.service.MemberService;
import com.fpt.ibom.profile.entity.LanguageLevel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class MemberSearchServiceTest {

	private final MemberSearchRepository searchRepository = org.mockito.Mockito.mock(MemberSearchRepository.class);
	private final SkillRepository skillRepository = org.mockito.Mockito.mock(SkillRepository.class);
	private final SeniorityRepository seniorityRepository = org.mockito.Mockito.mock(SeniorityRepository.class);
	private final LanguageRepository languageRepository = org.mockito.Mockito.mock(LanguageRepository.class);
	private final MemberService memberService = new MemberService(searchRepository, skillRepository,
			seniorityRepository, languageRepository);

	@Test
	void rejectsDuplicateUnknownNonPositiveAndMalformedConditions() {
		List<MemberSearchRequest> invalid = List.of(
				request(List.of(new MemberSearchRequest.SkillCondition(1L, null),
						new MemberSearchRequest.SkillCondition(1L, null)), null, 0, 10),
				request(List.of(new MemberSearchRequest.SkillCondition(0L, null)), null, 0, 10),
				request(null, List.of(new MemberSearchRequest.LanguageCondition(2L, "NATIVE"),
						new MemberSearchRequest.LanguageCondition(2L, null)), 0, 10),
				request(null, List.of(new MemberSearchRequest.LanguageCondition(null, "NATIVE")), 0, 10),
				request(null, List.of(new MemberSearchRequest.LanguageCondition(2L, "FLUENT")), 0, 10),
				request(null, null, -1, 10), request(null, null, 0, 0),
				new MemberSearchRequest(null, "UNKNOWN", null, null, 0, 10));

		for (MemberSearchRequest request : invalid) {
			assertThrows(ApiException.class, () -> memberService.search(request));
		}
		verifyNoInteractions(searchRepository, skillRepository, seniorityRepository, languageRepository);
	}

	@Test
	void mapsOptionalQualifiersAndAllSummaryEvidenceFields() {
		Skill skill = org.mockito.Mockito.mock(Skill.class);
		when(skill.getId()).thenReturn(1L);
		Language language = org.mockito.Mockito.mock(Language.class);
		when(language.getId()).thenReturn(2L);
		when(skillRepository.findAllById(List.of(1L))).thenReturn(List.of(skill));
		when(languageRepository.findAllById(List.of(2L))).thenReturn(List.of(language));

		MemberSearchRepository.MemberRow row = new MemberSearchRepository.MemberRow(7L, "Alice", "alice@example.com",
				UserStatus.INACTIVE, 2L, Instant.parse("2026-01-02T00:00:00Z"),
				Instant.parse("2026-01-04T00:00:00Z"));
		when(searchRepository.findMembers(any(), eq(PageRequest.of(0, 10))))
				.thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 10), 1));
		when(searchRepository.findMatchingProfiles(eq(List.of(7L)), any())).thenReturn(List.of(
				new MemberSearchRepository.ProfileMatchRow(7L, 11L, "CV", "A", "One", "Engineer",
						Instant.parse("2026-01-03T00:00:00Z"))));

		MemberSearchRequest request = request(List.of(new MemberSearchRequest.SkillCondition(1L, null)),
				List.of(new MemberSearchRequest.LanguageCondition(2L, null)), 0, 10);
		PageResponse<MemberSearchResponse> result = memberService.search(request);

		MemberSearchResponse response = result.content().get(0);
		assertEquals(UserStatus.INACTIVE, response.status());
		assertEquals(2L, response.activeProfileCount());
		assertEquals(Instant.parse("2026-01-04T00:00:00Z"), response.lastUpdatedAt());
		assertEquals(List.of(new MatchingProfileResponse(11L, "CV", "A", "One", "Engineer",
				Instant.parse("2026-01-03T00:00:00Z"))), response.matchingProfiles());

		ArgumentCaptor<MemberSearchRepository.SearchCriteria> criteria = ArgumentCaptor.forClass(
				MemberSearchRepository.SearchCriteria.class);
		verify(searchRepository).findMembers(criteria.capture(), eq(PageRequest.of(0, 10)));
		assertEquals(null, criteria.getValue().skills().get(0).fromExperience());
		assertEquals(null, criteria.getValue().skills().get(0).toExperience());
		assertEquals(null, criteria.getValue().languages().get(0).level());
		verify(seniorityRepository, never()).findAllById(any());
	}

	@Test
	void mapsSeniorityRangeAndLanguageLevel() {
		Skill skill = org.mockito.Mockito.mock(Skill.class);
		when(skill.getId()).thenReturn(1L);
		Language language = org.mockito.Mockito.mock(Language.class);
		when(language.getId()).thenReturn(2L);
		Seniority seniority = org.mockito.Mockito.mock(Seniority.class);
		when(seniority.getId()).thenReturn(3L);
		when(seniority.getFromExperience()).thenReturn(new BigDecimal("1.00"));
		when(seniority.getToExperience()).thenReturn(new BigDecimal("3.00"));
		when(skillRepository.findAllById(List.of(1L))).thenReturn(List.of(skill));
		when(seniorityRepository.findAllById(List.of(3L))).thenReturn(List.of(seniority));
		when(languageRepository.findAllById(List.of(2L))).thenReturn(List.of(language));
		when(searchRepository.findMembers(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

		memberService.search(request(List.of(new MemberSearchRequest.SkillCondition(1L, 3L)),
				List.of(new MemberSearchRequest.LanguageCondition(2L, " advanced ")), 0, 10));

		ArgumentCaptor<MemberSearchRepository.SearchCriteria> criteria = ArgumentCaptor.forClass(
				MemberSearchRepository.SearchCriteria.class);
		verify(searchRepository).findMembers(criteria.capture(), any(Pageable.class));
		MemberSearchRepository.SkillCriteria skillCriteria = criteria.getValue().skills().get(0);
		assertEquals(new BigDecimal("1.00"), skillCriteria.fromExperience());
		assertEquals(new BigDecimal("3.00"), skillCriteria.toExperience());
		assertEquals(LanguageLevel.ADVANCED, criteria.getValue().languages().get(0).level());
	}

	@Test
	void recoversOutOfRangePageAndDoesNotLoadEvidenceForEmptyResults() {
		when(searchRepository.findMembers(any(), any(Pageable.class))).thenReturn(
				new PageImpl<>(List.of(), PageRequest.of(4, 2), 5),
				new PageImpl<>(List.of(), PageRequest.of(2, 2), 5));

		PageResponse<MemberSearchResponse> result = memberService.search(request(null, null, 4, 2));

		assertEquals(2, result.page());
		assertEquals(3, result.totalPages());
		assertEquals(List.of(), result.content());
		verify(searchRepository, never()).findMatchingProfiles(any(), any());
	}

	private MemberSearchRequest request(List<MemberSearchRequest.SkillCondition> skills,
			List<MemberSearchRequest.LanguageCondition> languages, int page, int size) {
		return new MemberSearchRequest(null, null, skills, languages, page, size);
	}
}
