package com.fpt.ibom.master;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import com.fpt.ibom.common.PageResponse;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.master.entity.Skill;
import com.fpt.ibom.master.entity.SkillCategory;
import com.fpt.ibom.master.repository.SkillRepository;
import com.fpt.ibom.master.service.SkillService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class SkillServiceTest {

	private final SkillRepository skillRepository = org.mockito.Mockito.mock(SkillRepository.class);
	private final SkillService skillService = new SkillService(skillRepository);

	@Test
	void mapsPageMetadataSkillCategoryAndTimestampsWithCaseInsensitiveOrdering() {
		Skill skill = skill(12L, "Java", category(4L, "BACKEND", "Backend"));
		Page<Skill> skills = new PageImpl<>(List.of(skill), org.springframework.data.domain.PageRequest.of(1, 2), 5);
		when(skillRepository.findAll(any(Pageable.class))).thenReturn(skills);

		PageResponse<com.fpt.ibom.master.dto.SkillResponse> result = skillService.list(1, 2, null);

		assertEquals(1, result.page());
		assertEquals(2, result.size());
		assertEquals(5, result.totalElements());
		assertEquals(3, result.totalPages());
		assertEquals(12L, result.content().get(0).id());
		assertEquals("Java", result.content().get(0).name());
		assertEquals(4L, result.content().get(0).categoryId());
		assertEquals("BACKEND", result.content().get(0).categoryCode());
		assertEquals("Backend", result.content().get(0).categoryName());
		assertEquals(Instant.parse("2026-01-01T00:00:00Z"), result.content().get(0).createdAt());
		assertEquals(Instant.parse("2026-01-02T00:00:00Z"), result.content().get(0).updatedAt());

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(skillRepository).findAll(pageable.capture());
		assertEquals(1, pageable.getValue().getPageNumber());
		assertEquals(2, pageable.getValue().getPageSize());
		assertTrue(pageable.getValue().getSort().getOrderFor("name").isIgnoreCase());
		assertEquals("ASC", pageable.getValue().getSort().getOrderFor("name").getDirection().name());
		assertEquals("ASC", pageable.getValue().getSort().getOrderFor("id").getDirection().name());
	}

	@Test
	void trimsSearchAndDelegatesCaseInsensitiveContainsQuery() {
		when(skillRepository.findByNameContainingIgnoreCase(eq("spring"), any(Pageable.class)))
				.thenReturn(Page.empty());

		skillService.list(0, 10, "  spring  ");

		verify(skillRepository).findByNameContainingIgnoreCase(eq("spring"), any(Pageable.class));
		verify(skillRepository, never()).findAll(any(Pageable.class));
	}

	@Test
	void treatsBlankSearchAsNoFilter() {
		when(skillRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

		skillService.list(0, 10, " \t ");

		verify(skillRepository).findAll(any(Pageable.class));
		verify(skillRepository, never()).findByNameContainingIgnoreCase(any(), any(Pageable.class));
	}

	@Test
	void rejectsInvalidPaginationBeforeRepositoryAccess() {
		assertThrows(ApiException.class, () -> skillService.list(-1, 10, null));
		assertThrows(ApiException.class, () -> skillService.list(0, 0, null));
		verifyNoInteractions(skillRepository);
	}

	private Skill skill(Long id, String name, SkillCategory category) {
		Skill skill = new Skill(name, category);
		ReflectionTestUtils.setField(skill, "id", id);
		ReflectionTestUtils.setField(skill, "createdAt", Instant.parse("2026-01-01T00:00:00Z"));
		ReflectionTestUtils.setField(skill, "updatedAt", Instant.parse("2026-01-02T00:00:00Z"));
		return skill;
	}

	private SkillCategory category(Long id, String code, String name) {
		SkillCategory category = new SkillCategory(code, name);
		ReflectionTestUtils.setField(category, "id", id);
		return category;
	}
}
