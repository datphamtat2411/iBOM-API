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
import java.util.Optional;

import com.fpt.ibom.common.PageResponse;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.master.dto.LanguageRequest;
import com.fpt.ibom.master.dto.LanguageResponse;
import com.fpt.ibom.master.entity.Language;
import com.fpt.ibom.master.repository.LanguageRepository;
import com.fpt.ibom.master.service.LanguageService;
import com.fpt.ibom.profile.service.ProfileLanguageService;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class LanguageServiceTest {

	private final LanguageRepository languageRepository = org.mockito.Mockito.mock(LanguageRepository.class);
	private final ProfileLanguageService profileLanguageService = org.mockito.Mockito.mock(ProfileLanguageService.class);
	private final LanguageService languageService = new LanguageService(languageRepository, profileLanguageService);

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

	@Test
	void createsLanguageWithTrimmedName() {
		when(languageRepository.existsByNameIgnoreCase("English")).thenReturn(false);
		when(languageRepository.saveAndFlush(any(Language.class))).thenAnswer(invocation -> invocation.getArgument(0));

		LanguageResponse result = languageService.create(new LanguageRequest("  English  "));

		assertEquals("English", result.name());
		ArgumentCaptor<Language> language = ArgumentCaptor.forClass(Language.class);
		verify(languageRepository).saveAndFlush(language.capture());
		assertEquals("English", language.getValue().getName());
	}

	@Test
	void updatesLanguageWithTrimmedNameAndAllowsCurrentName() {
		Language existing = language(12L, "English");
		when(languageRepository.findById(12L)).thenReturn(Optional.of(existing));
		when(languageRepository.existsByNameIgnoreCaseAndIdNot("English", 12L)).thenReturn(false);
		when(languageRepository.saveAndFlush(existing)).thenReturn(existing);

		LanguageResponse result = languageService.update(12L, new LanguageRequest("  English  "));

		assertEquals("English", result.name());
		assertEquals("English", existing.getName());
	}

	@Test
	void rejectsNullBlankAndOverLengthNames() {
		assertError(ErrorCode.VALIDATION_ERROR, () -> languageService.create(new LanguageRequest(null)));
		assertError(ErrorCode.VALIDATION_ERROR, () -> languageService.create(new LanguageRequest(" \t ")));
		assertError(ErrorCode.VALIDATION_ERROR,
				() -> languageService.create(new LanguageRequest("a".repeat(256))));
		verifyNoInteractions(languageRepository, profileLanguageService);
	}

	@Test
	void rejectsCaseInsensitiveDuplicateOnCreateAndUpdate() {
		when(languageRepository.existsByNameIgnoreCase("english")).thenReturn(true);
		assertError(ErrorCode.LANGUAGE_ALREADY_EXISTS,
				() -> languageService.create(new LanguageRequest(" english ")));

		Language existing = language(12L, "English");
		when(languageRepository.findById(12L)).thenReturn(Optional.of(existing));
		when(languageRepository.existsByNameIgnoreCaseAndIdNot("Vietnamese", 12L)).thenReturn(true);
		assertError(ErrorCode.LANGUAGE_ALREADY_EXISTS,
				() -> languageService.update(12L, new LanguageRequest("Vietnamese")));
		verify(languageRepository, never()).saveAndFlush(any(Language.class));
	}

	@Test
	void returnsNotFoundForUnknownUpdateAndDeleteIds() {
		when(languageRepository.findById(99L)).thenReturn(Optional.empty());

		assertError(ErrorCode.LANGUAGE_NOT_FOUND, () -> languageService.update(99L, new LanguageRequest("English")));
		assertError(ErrorCode.LANGUAGE_NOT_FOUND, () -> languageService.delete(99L));
	}

	@Test
	void rejectsDeleteWhenLanguageIsReferencedWithoutDeletingAnyRecord() {
		Language existing = language(12L, "English");
		when(languageRepository.findById(12L)).thenReturn(Optional.of(existing));
		when(profileLanguageService.existsByLanguageId(12L)).thenReturn(true);

		assertError(ErrorCode.LANGUAGE_IN_USE, () -> languageService.delete(12L));
		verify(languageRepository, never()).delete(any(Language.class));
	}

	@Test
	void mapsDatabaseUniquenessAndForeignKeyConflicts() {
		when(languageRepository.existsByNameIgnoreCase("English")).thenReturn(false);
		when(languageRepository.saveAndFlush(any(Language.class))).thenThrow(dataIntegrity("uk_languages_name_ci"));
		assertError(ErrorCode.LANGUAGE_ALREADY_EXISTS,
				() -> languageService.create(new LanguageRequest("English")));

		Language existing = language(12L, "English");
		when(languageRepository.findById(12L)).thenReturn(Optional.of(existing));
		when(profileLanguageService.existsByLanguageId(12L)).thenReturn(false);
		org.mockito.Mockito.doThrow(dataIntegrity("fk_profile_languages_language"))
				.when(languageRepository).delete(existing);
		assertError(ErrorCode.LANGUAGE_IN_USE, () -> languageService.delete(12L));
	}

	private void assertError(ErrorCode errorCode, Runnable action) {
		ApiException exception = assertThrows(ApiException.class, action::run);
		assertEquals(HttpStatus.valueOf(exception.getStatus().value()), exception.getStatus());
		assertEquals(errorCode, exception.getErrorCode());
	}

	private DataIntegrityViolationException dataIntegrity(String constraintName) {
		return new DataIntegrityViolationException("constraint", new ConstraintViolationException("constraint", null,
				constraintName));
	}

	private Language language(Long id, String name) {
		Language language = new Language(name);
		ReflectionTestUtils.setField(language, "id", id);
		ReflectionTestUtils.setField(language, "createdAt", Instant.parse("2026-01-01T00:00:00Z"));
		ReflectionTestUtils.setField(language, "updatedAt", Instant.parse("2026-01-02T00:00:00Z"));
		return language;
	}
}
