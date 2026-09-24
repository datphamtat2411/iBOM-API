package com.fpt.ibom.member;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.common.PageResponse;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.master.repository.LanguageRepository;
import com.fpt.ibom.master.repository.SeniorityRepository;
import com.fpt.ibom.master.repository.SkillRepository;
import com.fpt.ibom.member.dto.MemberSummaryResponse;
import com.fpt.ibom.member.repository.MemberSearchRepository;
import com.fpt.ibom.member.service.MemberService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class MemberServiceTest {

	private final MemberSearchRepository searchRepository = org.mockito.Mockito.mock(MemberSearchRepository.class);
	private final SkillRepository skillRepository = org.mockito.Mockito.mock(SkillRepository.class);
	private final SeniorityRepository seniorityRepository = org.mockito.Mockito.mock(SeniorityRepository.class);
	private final LanguageRepository languageRepository = org.mockito.Mockito.mock(LanguageRepository.class);
	private final MemberService memberService = new MemberService(searchRepository, skillRepository,
			seniorityRepository, languageRepository);

	@Test
	void rejectsInvalidPaginationBeforeRepositoryAccess() {
		assertThrows(ApiException.class, () -> memberService.list(-1, 10, null, null));
		assertThrows(ApiException.class, () -> memberService.list(0, 0, null, null));
		verifyNoInteractions(searchRepository);
	}

	@Test
	void trimsSearchForwardsStatusAndMapsSixFieldsWithMaximumTimestamp() {
		MemberSearchRepository.MemberRow row = row(12L, "Alice", "alice@example.com", UserStatus.INACTIVE, 2L,
				Instant.parse("2026-01-03T00:00:00Z"), Instant.parse("2026-01-04T00:00:00Z"));
		when(searchRepository.findMembers(any(), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(row), org.springframework.data.domain.PageRequest.of(1, 2), 3));

		PageResponse<MemberSummaryResponse> result = memberService.list(1, 2, "  alice  ", UserStatus.INACTIVE);

		assertEquals(1, result.page());
		assertEquals(2, result.size());
		assertEquals(3, result.totalElements());
		MemberSummaryResponse member = result.content().get(0);
		assertEquals(12L, member.id());
		assertEquals("Alice", member.username());
		assertEquals("alice@example.com", member.email());
		assertEquals(UserStatus.INACTIVE, member.status());
		assertEquals(2L, member.activeProfileCount());
		assertEquals(Instant.parse("2026-01-04T00:00:00Z"), member.lastUpdatedAt());
		ArgumentCaptor<MemberSearchRepository.SearchCriteria> criteria = ArgumentCaptor.forClass(
				MemberSearchRepository.SearchCriteria.class);
		verify(searchRepository).findMembers(criteria.capture(), any(Pageable.class));
		assertEquals("alice", criteria.getValue().search());
		assertEquals(UserStatus.INACTIVE, criteria.getValue().status());
		assertEquals(List.of(), criteria.getValue().skills());
		assertEquals(List.of(), criteria.getValue().languages());
	}

	@Test
	void treatsBlankSearchAsAbsent() {
		when(searchRepository.findMembers(any(), any(Pageable.class))).thenReturn(org.springframework.data.domain.Page.empty());

		memberService.list(0, 10, " \t ", null);

		ArgumentCaptor<MemberSearchRepository.SearchCriteria> criteria = ArgumentCaptor.forClass(
				MemberSearchRepository.SearchCriteria.class);
		verify(searchRepository).findMembers(criteria.capture(), any(Pageable.class));
		assertEquals(null, criteria.getValue().search());
	}

	@Test
	void recoversToLastValidPage() {
		MemberSearchRepository.MemberRow row = row(12L, "Alice", "alice@example.com", UserStatus.ACTIVE, 0L,
				Instant.parse("2026-01-01T00:00:00Z"), null);
		when(searchRepository.findMembers(any(), any(Pageable.class))).thenReturn(
				new PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(4, 2), 5),
				new PageImpl<>(List.of(row), org.springframework.data.domain.PageRequest.of(2, 2), 5));

		PageResponse<MemberSummaryResponse> result = memberService.list(4, 2, null, null);

		assertEquals(2, result.page());
		assertEquals(3, result.totalPages());
		assertEquals("Alice", result.content().get(0).username());
		ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
		InOrder order = org.mockito.Mockito.inOrder(searchRepository);
		order.verify(searchRepository, times(2)).findMembers(any(), pageableCaptor.capture());
		order.verifyNoMoreInteractions();
		assertEquals(List.of(4, 2), pageableCaptor.getAllValues().stream().map(Pageable::getPageNumber).toList());
		assertEquals(List.of(2, 2), pageableCaptor.getAllValues().stream().map(Pageable::getPageSize).toList());
	}

	@Test
	void normalizesEmptyDatasetToPageZero() {
		when(searchRepository.findMembers(any(), any(Pageable.class))).thenReturn(org.springframework.data.domain.Page.empty());

		PageResponse<MemberSummaryResponse> result = memberService.list(9, 10, null, null);

		assertEquals(List.of(), result.content());
		assertEquals(0, result.page());
		assertEquals(10, result.size());
		assertEquals(0, result.totalElements());
		assertEquals(0, result.totalPages());
		verify(searchRepository, never()).findMatchingProfiles(any(), any());
	}

	private MemberSearchRepository.MemberRow row(Long id, String username, String email, UserStatus status,
			Long activeProfileCount, Instant accountUpdatedAt, Instant profileUpdatedAt) {
		return new MemberSearchRepository.MemberRow(id, username, email, status, activeProfileCount, accountUpdatedAt,
				profileUpdatedAt);
	}
}
