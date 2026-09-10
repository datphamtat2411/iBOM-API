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
import com.fpt.ibom.master.entity.Language;
import com.fpt.ibom.master.repository.LanguageRepository;
import com.fpt.ibom.master.service.LanguageService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class LanguageServiceTest {

	private final LanguageRepository languageRepository = org.mockito.Mockito.mock(LanguageRepository.class);
	private final LanguageService languageService = new LanguageService(languageRepository);

	@Test
	void mapsPageMetadataAndLanguageTimestampsWithCaseInsensitiveOrdering() {
		Language language = language(12L, "English");
		Page<Language> languages = new PageImpl<>(List.of(language), org.springframework.data.domain.PageRequest.of(1, 2), 5);
		when(languageRepository.findAll(any(Pageable.class))).thenReturn(languages);

		PageResponse<com.fpt.ibom.master.dto.LanguageResponse> result = languageService.list(1, 2, null);

		assertEquals(1, result.page());
		assertEquals(2, result.size());
		assertEquals(5, result.totalElements());
		assertEquals(3, result.totalPages());
		assertEquals(12L, result.content().get(0).id());
		assertEquals("English", result.content().get(0).name());
		assertEquals(Instant.parse("2026-01-01T00:00:00Z"), result.content().get(0).createdAt());
		assertEquals(Instant.parse("2026-01-02T00:00:00Z"), result.content().get(0).updatedAt());

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(languageRepository).findAll(pageable.capture());
		assertEquals(1, pageable.getValue().getPageNumber());
		assertEquals(2, pageable.getValue().getPageSize());
		assertTrue(pageable.getValue().getSort().getOrderFor("name").isIgnoreCase());
		assertEquals("ASC", pageable.getValue().getSort().getOrderFor("name").getDirection().name());
		assertEquals("ASC", pageable.getValue().getSort().getOrderFor("id").getDirection().name());
	}

	@Test
	void trimsSearchAndDelegatesCaseInsensitiveContainsQuery() {
		when(languageRepository.findByNameContainingIgnoreCase(eq("viet"), any(Pageable.class)))
				.thenReturn(Page.empty());

		languageService.list(0, 10, "  viet  ");

		verify(languageRepository).findByNameContainingIgnoreCase(eq("viet"), any(Pageable.class));
		verify(languageRepository, never()).findAll(any(Pageable.class));
	}

	@Test
	void treatsBlankSearchAsNoFilter() {
		when(languageRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

		languageService.list(0, 10, " \t ");

		verify(languageRepository).findAll(any(Pageable.class));
		verify(languageRepository, never()).findByNameContainingIgnoreCase(any(), any(Pageable.class));
	}

	@Test
	void rejectsInvalidPaginationBeforeRepositoryAccess() {
		assertThrows(ApiException.class, () -> languageService.list(-1, 10, null));
		assertThrows(ApiException.class, () -> languageService.list(0, 0, null));
		verifyNoInteractions(languageRepository);
	}

	private Language language(Long id, String name) {
		Language language = new Language(name);
		ReflectionTestUtils.setField(language, "id", id);
		ReflectionTestUtils.setField(language, "createdAt", Instant.parse("2026-01-01T00:00:00Z"));
		ReflectionTestUtils.setField(language, "updatedAt", Instant.parse("2026-01-02T00:00:00Z"));
		return language;
	}
}
