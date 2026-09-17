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
import com.fpt.ibom.master.dto.FileNameFormatRequest;
import com.fpt.ibom.master.dto.FileNameFormatResponse;
import com.fpt.ibom.master.entity.FileNameFormat;
import com.fpt.ibom.master.repository.FileNameFormatRepository;
import com.fpt.ibom.master.service.FileNameFormatService;
import com.fpt.ibom.profile.service.ProfileFileNameFormatReferenceService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class FileNameFormatServiceTest {

	private final FileNameFormatRepository fileNameFormatRepository = org.mockito.Mockito.mock(FileNameFormatRepository.class);
	private final ProfileFileNameFormatReferenceService profileReferences = org.mockito.Mockito
			.mock(ProfileFileNameFormatReferenceService.class);
	private final FileNameFormatService fileNameFormatService = new FileNameFormatService(fileNameFormatRepository,
			profileReferences);

	@Test
	void mapsPageMetadataAndSortsByNameCaseInsensitively() {
		FileNameFormat format = format(12L, "Alpha", "{LastName}_{Date}", false);
		Page<FileNameFormat> formats = new PageImpl<>(List.of(format), org.springframework.data.domain.PageRequest.of(1, 2), 5);
		when(fileNameFormatRepository.findAll(any(Pageable.class))).thenReturn(formats);

		PageResponse<FileNameFormatResponse> result = fileNameFormatService.list(1, 2);

		assertEquals(1, result.page());
		assertEquals(2, result.size());
		assertEquals(5, result.totalElements());
		assertEquals(3, result.totalPages());
		assertEquals(12L, result.content().get(0).id());
		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(fileNameFormatRepository).findAll(pageable.capture());
		assertTrue(pageable.getValue().getSort().getOrderFor("name").isIgnoreCase());
		assertEquals("ASC", pageable.getValue().getSort().getOrderFor("name").getDirection().name());
		assertEquals("ASC", pageable.getValue().getSort().getOrderFor("id").getDirection().name());
	}

	@Test
	void trimsNameAndPatternAndNeverAllowsCreateToSetDefault() {
		when(fileNameFormatRepository.existsByNameIgnoreCase("Custom")).thenReturn(false);
		when(fileNameFormatRepository.saveAndFlush(any(FileNameFormat.class))).thenAnswer(invocation -> invocation.getArgument(0));

		fileNameFormatService.create(new FileNameFormatRequest("  Custom  ", "  {LastName}_{Date}  "));

		ArgumentCaptor<FileNameFormat> format = ArgumentCaptor.forClass(FileNameFormat.class);
		verify(fileNameFormatRepository).saveAndFlush(format.capture());
		assertEquals("Custom", format.getValue().getName());
		assertEquals("{LastName}_{Date}", format.getValue().getPattern());
		assertEquals(false, format.getValue().isDefault());
	}

	@Test
	void rejectsDuplicateNamesCaseInsensitively() {
		when(fileNameFormatRepository.existsByNameIgnoreCase("system default")).thenReturn(true);

		ApiException exception = assertThrows(ApiException.class,
				() -> fileNameFormatService.create(new FileNameFormatRequest(" system default ", "{Date}")));

		assertEquals(ErrorCode.FILE_NAME_FORMAT_NAME_ALREADY_EXISTS, exception.getErrorCode());
		assertEquals(409, exception.getStatus().value());
		verify(fileNameFormatRepository, never()).saveAndFlush(any());
	}

	@Test
	void rejectsInvalidPaginationBeforeRepositoryAccess() {
		assertThrows(ApiException.class, () -> fileNameFormatService.list(-1, 10));
		assertThrows(ApiException.class, () -> fileNameFormatService.list(0, 0));
		verifyNoInteractions(fileNameFormatRepository);
	}

	@Test
	void rejectsInvalidPatterns() {
		for (String pattern : List.of(" ", "{LastName", "LastName}", "{Unknown}", "prefix/{Date}", "literal", "{Date}{Date}")) {
			ApiException exception = assertThrows(ApiException.class,
					() -> fileNameFormatService.create(new FileNameFormatRequest("Format " + pattern, pattern)));
			assertEquals(ErrorCode.FILE_NAME_FORMAT_INVALID, exception.getErrorCode());
		}
	}

	@Test
	void acceptsSupportedUniquePlaceholders() {
		when(fileNameFormatRepository.existsByNameIgnoreCase("Format")).thenReturn(false);
		when(fileNameFormatRepository.saveAndFlush(any(FileNameFormat.class))).thenAnswer(invocation -> invocation.getArgument(0));

		fileNameFormatService.create(new FileNameFormatRequest("Format", "{LastName}-{FirstName}_{Role}_{Date}"));

		verify(fileNameFormatRepository).saveAndFlush(any(FileNameFormat.class));
	}

	@Test
	void updatePreservesDefaultFlagAndOnlyChangesManagedFields() {
		FileNameFormat existing = format(4L, "System Default", "{LastName}_{Date}", true);
		when(fileNameFormatRepository.findById(4L)).thenReturn(Optional.of(existing));
		when(fileNameFormatRepository.existsByNameIgnoreCaseAndIdNot("Renamed", 4L)).thenReturn(false);
		when(fileNameFormatRepository.saveAndFlush(existing)).thenReturn(existing);

		FileNameFormatResponse result = fileNameFormatService.update(4L,
				new FileNameFormatRequest(" Renamed ", " {FirstName}_{Date} "));

		assertEquals("Renamed", result.name());
		assertEquals("{FirstName}_{Date}", result.pattern());
		assertTrue(result.isDefault());
		verify(fileNameFormatRepository).saveAndFlush(existing);
	}

	@Test
	void protectsDefaultAndReferencedFormatsFromDeletion() {
		FileNameFormat defaultFormat = format(1L, "System Default", "{Date}", true);
		when(fileNameFormatRepository.findById(1L)).thenReturn(Optional.of(defaultFormat));
		ApiException defaultException = assertThrows(ApiException.class, () -> fileNameFormatService.delete(1L));
		assertEquals(ErrorCode.FILE_NAME_FORMAT_DEFAULT_CANNOT_DELETE, defaultException.getErrorCode());

		FileNameFormat referenced = format(2L, "Custom", "{Date}", false);
		when(fileNameFormatRepository.findById(2L)).thenReturn(Optional.of(referenced));
		when(profileReferences.isReferenced(2L)).thenReturn(true);
		ApiException referenceException = assertThrows(ApiException.class, () -> fileNameFormatService.delete(2L));
		assertEquals(ErrorCode.FILE_NAME_FORMAT_REFERENCED_BY_PROFILE, referenceException.getErrorCode());
	}

	@Test
	void rejectsMissingRecords() {
		when(fileNameFormatRepository.findById(8L)).thenReturn(Optional.empty());

		ApiException exception = assertThrows(ApiException.class, () -> fileNameFormatService.update(8L,
				new FileNameFormatRequest("Format", "{Date}")));

		assertEquals(ErrorCode.FILE_NAME_FORMAT_NOT_FOUND, exception.getErrorCode());
	}

	private FileNameFormat format(Long id, String name, String pattern, boolean isDefault) {
		FileNameFormat format = new FileNameFormat(name, pattern, isDefault);
		ReflectionTestUtils.setField(format, "id", id);
		ReflectionTestUtils.setField(format, "createdAt", Instant.parse("2026-01-01T00:00:00Z"));
		ReflectionTestUtils.setField(format, "updatedAt", Instant.parse("2026-01-02T00:00:00Z"));
		return format;
	}
}
