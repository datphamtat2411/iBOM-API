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
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.common.PageResponse;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.master.repository.SeniorityRepository;
import com.fpt.ibom.master.repository.SkillRepository;
import com.fpt.ibom.member.dto.MemberSummaryResponse;
import com.fpt.ibom.member.repository.MemberSkillSearchRepository;
import com.fpt.ibom.member.repository.MemberSummaryProjection;
import com.fpt.ibom.member.service.MemberService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class MemberServiceTest {

	private final UserAccountRepository userAccountRepository = org.mockito.Mockito.mock(UserAccountRepository.class);
	private final MemberSkillSearchRepository memberSkillSearchRepository = org.mockito.Mockito
			.mock(MemberSkillSearchRepository.class);
	private final SkillRepository skillRepository = org.mockito.Mockito.mock(SkillRepository.class);
	private final SeniorityRepository seniorityRepository = org.mockito.Mockito.mock(SeniorityRepository.class);
	private final MemberService memberService = new MemberService(userAccountRepository, memberSkillSearchRepository,
			skillRepository, seniorityRepository);

	@Test
	void rejectsInvalidPaginationBeforeRepositoryAccess() {
		assertThrows(ApiException.class, () -> memberService.list(-1, 10, null, null));
		assertThrows(ApiException.class, () -> memberService.list(0, 0, null, null));
		verifyNoInteractions(userAccountRepository);
	}

	@Test
	void trimsSearchForwardsStatusAndMapsSixFieldsWithMaximumTimestamp() {
		MemberSummaryProjection projection = projection(12L, "Alice", "alice@example.com", UserStatus.INACTIVE, 2L,
				Instant.parse("2026-01-03T00:00:00Z"), Instant.parse("2026-01-04T00:00:00Z"));
		when(userAccountRepository.findMemberSummaries(eq(UserStatus.INACTIVE), eq("alice"), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(projection), org.springframework.data.domain.PageRequest.of(1, 2), 3));

		PageResponse<MemberSummaryResponse> result = memberService.list(1, 2, "  alice  ", UserStatus.INACTIVE);

		assertEquals(1, result.page());
		assertEquals(2, result.size());
		assertEquals(3, result.totalElements());
		assertEquals(2, result.totalPages());
		MemberSummaryResponse member = result.content().get(0);
		assertEquals(12L, member.id());
		assertEquals("Alice", member.username());
		assertEquals("alice@example.com", member.email());
		assertEquals(UserStatus.INACTIVE, member.status());
		assertEquals(2L, member.activeProfileCount());
		assertEquals(Instant.parse("2026-01-04T00:00:00Z"), member.lastUpdatedAt());
		verify(userAccountRepository).findMemberSummaries(eq(UserStatus.INACTIVE), eq("alice"), any(Pageable.class));
	}

	@Test
	void treatsBlankSearchAsAbsent() {
		when(userAccountRepository.findMemberSummaries(isNull(), isNull(), any(Pageable.class))).thenReturn(Page.empty());

		memberService.list(0, 10, " \t ", null);

		verify(userAccountRepository).findMemberSummaries(isNull(), isNull(), any(Pageable.class));
		verify(userAccountRepository, never()).findMemberSummaries(any(UserStatus.class), any(String.class), any(Pageable.class));
	}

	@Test
	void recoversToLastValidPage() {
		MemberSummaryProjection projection = projection(12L, "Alice", "alice@example.com", UserStatus.ACTIVE, 0L,
				Instant.parse("2026-01-01T00:00:00Z"), null);
		when(userAccountRepository.findMemberSummaries(isNull(), isNull(), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(4, 2), 5),
						new PageImpl<>(List.of(projection), org.springframework.data.domain.PageRequest.of(2, 2), 5));

		PageResponse<MemberSummaryResponse> result = memberService.list(4, 2, null, null);

		assertEquals(2, result.page());
		assertEquals(3, result.totalPages());
		assertEquals("Alice", result.content().get(0).username());
		ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
		InOrder order = org.mockito.Mockito.inOrder(userAccountRepository);
		order.verify(userAccountRepository, times(2)).findMemberSummaries(isNull(), isNull(), pageableCaptor.capture());
		order.verifyNoMoreInteractions();
		assertEquals(List.of(4, 2), pageableCaptor.getAllValues().stream().map(Pageable::getPageNumber).toList());
		assertEquals(List.of(2, 2), pageableCaptor.getAllValues().stream().map(Pageable::getPageSize).toList());
	}

	@Test
	void normalizesEmptyDatasetToPageZero() {
		when(userAccountRepository.findMemberSummaries(isNull(), isNull(), any(Pageable.class))).thenReturn(Page.empty());

		PageResponse<MemberSummaryResponse> result = memberService.list(9, 10, null, null);

		assertEquals(List.of(), result.content());
		assertEquals(0, result.page());
		assertEquals(10, result.size());
		assertEquals(0, result.totalElements());
		assertEquals(0, result.totalPages());
	}

	private MemberSummaryProjection projection(Long id, String username, String email, UserStatus status,
			Long activeProfileCount, Instant accountUpdatedAt, Instant profileUpdatedAt) {
		MemberSummaryProjection projection = org.mockito.Mockito.mock(MemberSummaryProjection.class);
		when(projection.getId()).thenReturn(id);
		when(projection.getUsername()).thenReturn(username);
		when(projection.getEmail()).thenReturn(email);
		when(projection.getStatus()).thenReturn(status);
		when(projection.getActiveProfileCount()).thenReturn(activeProfileCount);
		when(projection.getAccountUpdatedAt()).thenReturn(accountUpdatedAt);
		when(projection.getProfileUpdatedAt()).thenReturn(profileUpdatedAt);
		return projection;
	}
}
