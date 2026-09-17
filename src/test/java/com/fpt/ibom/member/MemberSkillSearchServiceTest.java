package com.fpt.ibom.member;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Arrays;

import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.common.PageResponse;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.master.entity.Seniority;
import com.fpt.ibom.master.entity.Skill;
import com.fpt.ibom.master.repository.SeniorityRepository;
import com.fpt.ibom.master.repository.SkillRepository;
import com.fpt.ibom.member.dto.MemberSkillSearchRequest;
import com.fpt.ibom.member.dto.MemberSkillSearchResponse;
import com.fpt.ibom.member.dto.MatchingProfileResponse;
import com.fpt.ibom.member.dto.SkillSeniorityMatchResponse;
import com.fpt.ibom.member.repository.MemberSkillSearchRepository;
import com.fpt.ibom.member.service.MemberService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class MemberSkillSearchServiceTest {

	private final MemberSkillSearchRepository searchRepository = org.mockito.Mockito.mock(MemberSkillSearchRepository.class);
	private final SkillRepository skillRepository = org.mockito.Mockito.mock(SkillRepository.class);
	private final SeniorityRepository seniorityRepository = org.mockito.Mockito.mock(SeniorityRepository.class);
	private final MemberService memberService = new MemberService(searchRepository, skillRepository, seniorityRepository);

	@Test
	void rejectsMissingEmptyUnequalMalformedDuplicateAndNonPositiveIds() {
		List<MemberSkillSearchRequest> invalidRequests = List.of(
				new MemberSkillSearchRequest(null, List.of(2L), null, 0, 10),
				new MemberSkillSearchRequest(List.of(), List.of(2L), null, 0, 10),
				new MemberSkillSearchRequest(List.of(1L), null, null, 0, 10),
				new MemberSkillSearchRequest(List.of(1L, 2L), List.of(2L), null, 0, 10),
				new MemberSkillSearchRequest(Arrays.asList((Long) null), List.of(2L), null, 0, 10),
				new MemberSkillSearchRequest(List.of(0L), List.of(2L), null, 0, 10),
				new MemberSkillSearchRequest(List.of(1L, 1L), List.of(2L, 3L), null, 0, 10));

		for (MemberSkillSearchRequest request : invalidRequests) {
			ApiException exception = assertThrows(ApiException.class, () -> memberService.searchBySkill(request));
			assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
		}
		verifyNoInteractions(searchRepository, skillRepository, seniorityRepository);
	}

	@Test
	void rejectsUnknownSkillOrSeniorityBeforeSearching() {
		Skill skill = org.mockito.Mockito.mock(Skill.class);
		when(skill.getId()).thenReturn(1L);
		Seniority seniority = seniority(2L, "Mid", "1.00", "3.00");
		when(skillRepository.findAllById(List.of(1L))).thenReturn(List.of());

		assertThrows(ApiException.class, () -> memberService.searchBySkill(request(List.of(1L), List.of(2L))));
		when(skillRepository.findAllById(List.of(1L))).thenReturn(List.of(skill));
		when(seniorityRepository.findAllById(List.of(2L))).thenReturn(List.of());
		ApiException exception = assertThrows(ApiException.class,
				() -> memberService.searchBySkill(request(List.of(1L), List.of(2L))));

		assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
		verifyNoInteractions(searchRepository);
	}

	@Test
	void acceptsRepeatedSenioritiesPreservesPairOrderAndMapsOnlyMatchingEvidence() {
		Skill skillOne = org.mockito.Mockito.mock(Skill.class);
		Skill skillTwo = org.mockito.Mockito.mock(Skill.class);
		when(skillOne.getId()).thenReturn(1L);
		when(skillTwo.getId()).thenReturn(3L);
		Seniority seniority = seniority(2L, "Mid", "1.00", "3.00");
		when(skillRepository.findAllById(List.of(1L, 3L))).thenReturn(List.of(skillOne, skillTwo));
		when(seniorityRepository.findAllById(List.of(2L, 2L))).thenReturn(List.of(seniority));

		MemberSkillSearchRepository.MemberRow member = new MemberSkillSearchRepository.MemberRow(7L, "Alice",
				"alice@example.com", UserStatus.INACTIVE, 1L, Instant.parse("2026-01-02T00:00:00Z"),
				Instant.parse("2026-01-04T00:00:00Z"));
		when(searchRepository.findMembers(any(), eq(UserStatus.INACTIVE), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(member), org.springframework.data.domain.PageRequest.of(0, 10), 1));
		when(searchRepository.findMatchingProfiles(eq(List.of(7L)), any())).thenReturn(List.of(
				new MemberSkillSearchRepository.ProfileMatchRow(7L, 11L, "CV", "A", "One", "Engineer",
						Instant.parse("2026-01-03T00:00:00Z"), 1L, new BigDecimal("1.00")),
				new MemberSkillSearchRepository.ProfileMatchRow(7L, 11L, "CV", "A", "One", "Engineer",
						Instant.parse("2026-01-03T00:00:00Z"), 3L, new BigDecimal("2.00"))));

		PageResponse<MemberSkillSearchResponse> result = memberService.searchBySkill(
				new MemberSkillSearchRequest(List.of(1L, 3L), List.of(2L, 2L), UserStatus.INACTIVE, 0, 10));

		assertEquals(1, result.content().size());
		MemberSkillSearchResponse response = result.content().get(0);
		assertEquals(UserStatus.INACTIVE, response.status());
		MatchingProfileResponse profile = response.matchingProfiles().get(0);
		assertEquals(List.of(1L, 3L), profile.matches().stream().map(SkillSeniorityMatchResponse::skillId).toList());
		assertEquals(List.of(2L, 2L), profile.matches().stream().map(SkillSeniorityMatchResponse::seniorityId).toList());
		ArgumentCaptor<List<MemberSkillSearchRepository.Pair>> pairs = ArgumentCaptor.forClass(List.class);
		verify(searchRepository).findMembers(pairs.capture(), eq(UserStatus.INACTIVE), any(Pageable.class));
		assertEquals(List.of(1L, 3L), pairs.getValue().stream().map(MemberSkillSearchRepository.Pair::skillId).toList());
	}

	@Test
	void recoversOutOfRangePageAndNormalizesEmptyResults() {
		masterData(1L, 2L);
		MemberSkillSearchRepository.MemberRow row = new MemberSkillSearchRepository.MemberRow(7L, "Alice",
				"alice@example.com", UserStatus.ACTIVE, 0L, Instant.parse("2026-01-01T00:00:00Z"), null);
		when(searchRepository.findMembers(any(), eq(null), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(4, 2), 5),
						new PageImpl<>(List.of(row), org.springframework.data.domain.PageRequest.of(2, 2), 5));
		when(searchRepository.findMatchingProfiles(eq(List.of(7L)), any())).thenReturn(List.of());

		PageResponse<MemberSkillSearchResponse> result = memberService.searchBySkill(request(List.of(1L), List.of(2L), 4, 2));

		assertEquals(2, result.page());
		assertEquals(3, result.totalPages());
		assertEquals("Alice", result.content().get(0).username());

		when(searchRepository.findMembers(any(), eq(null), any(Pageable.class)))
				.thenReturn(new PageImpl<MemberSkillSearchRepository.MemberRow>(List.of()));
		result = memberService.searchBySkill(request(List.of(1L), List.of(2L), 9, 10));
		assertEquals(0, result.page());
		assertEquals(List.of(), result.content());
	}

	private MemberSkillSearchRequest request(List<Long> skills, List<Long> seniorities) {
		return request(skills, seniorities, 0, 10);
	}

	private MemberSkillSearchRequest request(List<Long> skills, List<Long> seniorities, int page, int size) {
		return new MemberSkillSearchRequest(skills, seniorities, null, page, size);
	}

	private void masterData(Long skillId, Long seniorityId) {
		Skill skill = org.mockito.Mockito.mock(Skill.class);
		when(skill.getId()).thenReturn(skillId);
		when(skillRepository.findAllById(List.of(skillId))).thenReturn(List.of(skill));
		Seniority seniority = seniority(seniorityId, "Mid", "1.00", "3.00");
		when(seniorityRepository.findAllById(List.of(seniorityId))).thenReturn(List.of(seniority));
	}

	private Seniority seniority(Long id, String name, String from, String to) {
		Seniority seniority = org.mockito.Mockito.mock(Seniority.class);
		when(seniority.getId()).thenReturn(id);
		when(seniority.getName()).thenReturn(name);
		when(seniority.getFromExperience()).thenReturn(new BigDecimal(from));
		when(seniority.getToExperience()).thenReturn(to == null ? null : new BigDecimal(to));
		return seniority;
	}
}
