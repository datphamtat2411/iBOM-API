package com.fpt.ibom.master;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.fpt.ibom.common.PageResponse;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.master.dto.SkillRequest;
import com.fpt.ibom.master.dto.SkillResponse;
import com.fpt.ibom.master.entity.Skill;
import com.fpt.ibom.master.entity.SkillCategory;
import com.fpt.ibom.master.repository.SkillCategoryRepository;
import com.fpt.ibom.master.repository.SkillRepository;
import com.fpt.ibom.profile.repository.ProfileSkillRepository;
import org.hibernate.exception.ConstraintViolationException;
import com.fpt.ibom.master.service.SkillService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class SkillServiceTest {

	private final SkillRepository skillRepository = org.mockito.Mockito.mock(SkillRepository.class);
	private final SkillCategoryRepository categoryRepository = org.mockito.Mockito.mock(SkillCategoryRepository.class);
	private final ProfileSkillRepository profileSkillRepository = org.mockito.Mockito.mock(ProfileSkillRepository.class);
	private final SkillService skillService = new SkillService(skillRepository, categoryRepository, profileSkillRepository);

	@Test
	void mapsPageMetadataSkillCategoryAndTimestampsWithCaseInsensitiveOrdering() {
		Skill skill = skill(12L, "Java", category(4L, "BACKEND", "Backend"));
		Page<Skill> skills = new PageImpl<>(List.of(skill), org.springframework.data.domain.PageRequest.of(1, 2), 5);
		when(skillRepository.findAll(any(Pageable.class))).thenReturn(skills);

		PageResponse<com.fpt.ibom.master.dto.SkillResponse> result = skillService.list(1, 2, null, null);

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

		skillService.list(0, 10, "  spring  ", null);

		verify(skillRepository).findByNameContainingIgnoreCase(eq("spring"), any(Pageable.class));
		verify(skillRepository, never()).findAll(any(Pageable.class));
	}

	@Test
	void treatsBlankSearchAsNoFilter() {
		when(skillRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

		skillService.list(0, 10, " \t ", null);

		verify(skillRepository).findAll(any(Pageable.class));
		verify(skillRepository, never()).findByNameContainingIgnoreCase(any(), any(Pageable.class));
	}

	@Test
	void filtersByCategoryAndPreservesPageableOrdering() {
		SkillCategory category = category(4L, "BACKEND", "Backend");
		when(categoryRepository.findById(4L)).thenReturn(Optional.of(category));
		when(skillRepository.findByCategoryId(eq(4L), any(Pageable.class))).thenReturn(Page.empty());

		skillService.list(1, 2, " \t ", 4L);

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(skillRepository).findByCategoryId(eq(4L), pageable.capture());
		assertEquals(1, pageable.getValue().getPageNumber());
		assertEquals(2, pageable.getValue().getPageSize());
		assertTrue(pageable.getValue().getSort().getOrderFor("name").isIgnoreCase());
		assertEquals("ASC", pageable.getValue().getSort().getOrderFor("name").getDirection().name());
		assertEquals("ASC", pageable.getValue().getSort().getOrderFor("id").getDirection().name());
		verify(skillRepository, never()).findAll(any(Pageable.class));
		verify(skillRepository, never()).findByNameContainingIgnoreCase(any(), any(Pageable.class));
	}

	@Test
	void composesCategoryAndTrimmedSearchWithAndSemantics() {
		SkillCategory category = category(4L, "BACKEND", "Backend");
		when(categoryRepository.findById(4L)).thenReturn(Optional.of(category));
		when(skillRepository.findByCategoryIdAndNameContainingIgnoreCase(eq(4L), eq("spring"), any(Pageable.class)))
				.thenReturn(Page.empty());

		skillService.list(0, 10, "  spring  ", 4L);

		verify(skillRepository).findByCategoryIdAndNameContainingIgnoreCase(eq(4L), eq("spring"), any(Pageable.class));
		verify(skillRepository, never()).findAll(any(Pageable.class));
		verify(skillRepository, never()).findByNameContainingIgnoreCase(any(), any(Pageable.class));
		verify(skillRepository, never()).findByCategoryId(eq(4L), any(Pageable.class));
	}

	@Test
	void rejectsUnknownCategoryBeforeRepositoryAccess() {
		when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

		ApiException exception = assertThrows(ApiException.class, () -> skillService.list(0, 10, null, 99L));

		assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
		assertEquals(ErrorCode.SKILL_CATEGORY_NOT_FOUND, exception.getErrorCode());
		verifyNoInteractions(skillRepository);
	}

	@Test
	void rejectsInvalidPaginationBeforeRepositoryAccess() {
		assertThrows(ApiException.class, () -> skillService.list(-1, 10, null, null));
		assertThrows(ApiException.class, () -> skillService.list(0, 0, null, null));
		verifyNoInteractions(skillRepository);
	}

	@Test
	void createsSkillWithTrimmedNameAndExistingCategory() {
		SkillCategory category = category(4L, "BACKEND", "Backend");
		when(categoryRepository.findById(4L)).thenReturn(Optional.of(category));
		when(skillRepository.existsByNameIgnoreCase("Java")).thenReturn(false);
		when(skillRepository.saveAndFlush(any(Skill.class))).thenAnswer(invocation -> invocation.getArgument(0));

		SkillResponse result = skillService.create(new SkillRequest("  Java  ", 4L));

		ArgumentCaptor<Skill> skill = ArgumentCaptor.forClass(Skill.class);
		verify(skillRepository).saveAndFlush(skill.capture());
		assertEquals("Java", skill.getValue().getName());
		assertEquals(category, skill.getValue().getCategory());
		assertEquals("Java", result.name());
		assertEquals(4L, result.categoryId());
	}

	@Test
	void updatesSkillWithTrimmedNameAndReplacementCategory() {
		SkillCategory originalCategory = category(4L, "BACKEND", "Backend");
		SkillCategory replacementCategory = category(5L, "DATABASE_DATA", "Database & Data");
		Skill existing = skill(12L, "Java", originalCategory);
		when(skillRepository.findById(12L)).thenReturn(Optional.of(existing));
		when(categoryRepository.findById(5L)).thenReturn(Optional.of(replacementCategory));
		when(skillRepository.existsByNameIgnoreCaseAndIdNot("Spring", 12L)).thenReturn(false);
		when(skillRepository.saveAndFlush(existing)).thenReturn(existing);

		SkillResponse result = skillService.update(12L, new SkillRequest(" Spring ", 5L));

		assertEquals("Spring", existing.getName());
		assertEquals(replacementCategory, existing.getCategory());
		assertEquals("Spring", result.name());
		assertEquals(5L, result.categoryId());
	}

	@Test
	void deletesUnreferencedSkillWithoutTouchingProfileSkills() {
		Skill existing = skill(12L, "Java", category(4L, "BACKEND", "Backend"));
		when(skillRepository.findById(12L)).thenReturn(Optional.of(existing));
		when(profileSkillRepository.existsBySkillId(12L)).thenReturn(false);

		skillService.delete(12L);

		verify(skillRepository).delete(existing);
		verify(skillRepository).flush();
		verifyNoInteractions(categoryRepository);
	}

	@Test
	void rejectsReferencedSkillDeletion() {
		Skill existing = skill(12L, "Java", category(4L, "BACKEND", "Backend"));
		when(skillRepository.findById(12L)).thenReturn(Optional.of(existing));
		when(profileSkillRepository.existsBySkillId(12L)).thenReturn(true);

		ApiException exception = assertThrows(ApiException.class, () -> skillService.delete(12L));

		assertEquals(HttpStatus.CONFLICT, exception.getStatus());
		assertEquals(ErrorCode.SKILL_REFERENCED_BY_PROFILES, exception.getErrorCode());
		verify(skillRepository, never()).delete(any(Skill.class));
	}

	@Test
	void rejectsInvalidNamesDuplicateNamesAndUnknownReferences() {
		ApiException blank = assertThrows(ApiException.class,
				() -> skillService.create(new SkillRequest("  ", 4L)));
		assertEquals(ErrorCode.VALIDATION_ERROR, blank.getErrorCode());
		verifyNoInteractions(skillRepository, categoryRepository, profileSkillRepository);

		when(categoryRepository.findById(4L)).thenReturn(Optional.of(category(4L, "BACKEND", "Backend")));
		when(skillRepository.existsByNameIgnoreCase("java")).thenReturn(true);
		ApiException duplicate = assertThrows(ApiException.class,
				() -> skillService.create(new SkillRequest("java", 4L)));
		assertEquals(HttpStatus.CONFLICT, duplicate.getStatus());
		assertEquals(ErrorCode.SKILL_NAME_ALREADY_EXISTS, duplicate.getErrorCode());

		when(categoryRepository.findById(99L)).thenReturn(Optional.empty());
		ApiException categoryMissing = assertThrows(ApiException.class,
				() -> skillService.create(new SkillRequest("Spring", 99L)));
		assertEquals(HttpStatus.NOT_FOUND, categoryMissing.getStatus());
		assertEquals(ErrorCode.SKILL_CATEGORY_NOT_FOUND, categoryMissing.getErrorCode());

		when(skillRepository.findById(99L)).thenReturn(Optional.empty());
		ApiException skillMissing = assertThrows(ApiException.class,
				() -> skillService.update(99L, new SkillRequest("Spring", 4L)));
		assertEquals(HttpStatus.NOT_FOUND, skillMissing.getStatus());
		assertEquals(ErrorCode.SKILL_NOT_FOUND, skillMissing.getErrorCode());
	}

	@Test
	void translatesDatabaseSkillNameConstraintConflict() {
		SkillCategory category = category(4L, "BACKEND", "Backend");
		when(categoryRepository.findById(4L)).thenReturn(Optional.of(category));
		when(skillRepository.existsByNameIgnoreCase("Java")).thenReturn(false);
		ConstraintViolationException violation = new ConstraintViolationException("duplicate", null,
				"uk_skills_name_ci");
		doThrow(new DataIntegrityViolationException("duplicate", violation)).when(skillRepository)
				.saveAndFlush(any(Skill.class));

		ApiException exception = assertThrows(ApiException.class,
				() -> skillService.create(new SkillRequest("Java", 4L)));

		assertEquals(HttpStatus.CONFLICT, exception.getStatus());
		assertEquals(ErrorCode.SKILL_NAME_ALREADY_EXISTS, exception.getErrorCode());
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
