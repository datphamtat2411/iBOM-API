package com.fpt.ibom.cv;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.cv.model.CvDocument;
import com.fpt.ibom.cv.model.CvExportResult;
import com.fpt.ibom.cv.model.CvPersonalDetails;
import com.fpt.ibom.cv.service.CvDocumentAssembler;
import com.fpt.ibom.cv.service.CvExportService;
import com.fpt.ibom.cv.service.CvPdfRenderer;
import com.fpt.ibom.cv.service.CvProfileAccessService;
import com.fpt.ibom.cv.renderer.CvDocxRenderer;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.service.ProfileVersionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class CvExportServiceTest {

	private static final Instant EXPORT_TIME = Instant.parse("2026-01-01T23:30:00Z");

	private final CvProfileAccessService access = org.mockito.Mockito.mock(CvProfileAccessService.class);
	private final FileNameResolutionServiceStub filenames = new FileNameResolutionServiceStub();
	private final CvDocumentAssembler assembler = org.mockito.Mockito.mock(CvDocumentAssembler.class);
	private final CvPdfRenderer pdfRenderer = org.mockito.Mockito.mock(CvPdfRenderer.class);
	private final CvDocxRenderer docxRenderer = org.mockito.Mockito.mock(CvDocxRenderer.class);
	private final ProfileVersionService versions = org.mockito.Mockito.mock(ProfileVersionService.class);
	private final Clock clock = Clock.fixed(EXPORT_TIME, ZoneOffset.UTC);
	private final CvExportService service = new CvExportService(access, filenames.service(), assembler, pdfRenderer,
			docxRenderer, versions, clock);

	@Test
	void exportsPdfThroughPdfRendererAndRecordsTheLogicalTimestamp() {
		Profile profile = profile(8L, 7L, 4L);
		CvDocument document = document();
		when(access.resolve(principal(7L, UserRole.MEMBER), 8L)).thenReturn(profile);
		when(assembler.assemble(8L)).thenReturn(document);
		when(pdfRenderer.render(document)).thenReturn(new byte[] { 1, 2 });
		filenames.returns("Last_20260102.pdf");

		CvExportResult result = service.export(principal(7L, UserRole.MEMBER), 8L, "pdf", null);

		assertArrayEquals(new byte[] { 1, 2 }, result.bytes());
		assertEquals("Last_20260102.pdf", result.fileName());
		assertEquals("pdf", result.format());
		verify(filenames.service()).resolve(profile, null, com.fpt.ibom.cv.model.DocumentFormat.PDF, EXPORT_TIME);
		verify(versions).markExported(profile, 4L, EXPORT_TIME);
		verifyNoInteractions(docxRenderer);
	}

	@Test
	void exportsDocxThroughDocxRenderer() {
		Profile profile = profile(8L, 7L, 4L);
		CvDocument document = document();
		when(access.resolve(any(), eq(8L))).thenReturn(profile);
		when(assembler.assemble(8L)).thenReturn(document);
		when(docxRenderer.render(document)).thenReturn(new byte[] { 9, 8 });
		filenames.returns("Last_20260102.docx");

		CvExportResult result = service.export(principal(7L, UserRole.MEMBER), 8L, "docx", null);

		assertArrayEquals(new byte[] { 9, 8 }, result.bytes());
		assertEquals("Last_20260102.docx", result.fileName());
		assertEquals("docx", result.format());
		verify(pdfRenderer, never()).render(any());
		verify(versions).markExported(profile, 4L, EXPORT_TIME);
	}

	@Test
	void invalidAndMissingFormatsFailBeforeContentWork() {
		Profile profile = profile(8L, 7L, 4L);
		when(access.resolve(any(), eq(8L))).thenReturn(profile);

		for (String format : new String[] { null, "", "html" }) {
			ApiException exception = assertThrows(ApiException.class,
					() -> service.export(principal(7L, UserRole.MEMBER), 8L, format, null));
			assertEquals(ErrorCode.CV_EXPORT_FORMAT_INVALID, exception.getErrorCode());
		}

		verifyNoInteractions(assembler, pdfRenderer, docxRenderer, versions);
		verify(filenames.service(), never()).resolve(any(), any(), any(), any());
	}

	@Test
	void unpreviewedProfilesAreRejectedForBothFormatsBeforeRendering() {
		Profile profile = profile(8L, 7L, 4L);
		profile.invalidatePreview();
		when(access.resolve(any(), eq(8L))).thenReturn(profile);

		for (String format : new String[] { "pdf", "docx" }) {
			ApiException exception = assertThrows(ApiException.class,
					() -> service.export(principal(7L, UserRole.MEMBER), 8L, format, null));
			assertEquals(ErrorCode.CV_PREVIEW_REQUIRED, exception.getErrorCode());
		}

		verifyNoInteractions(filenames.service(), assembler, pdfRenderer, docxRenderer, versions);
	}

	@Test
	void accessFailurePreventsAllCvDataAccess() {
		ApiException forbidden = new ApiException(HttpStatus.FORBIDDEN, ErrorCode.REQUEST_FAILED, "Forbidden");
		when(access.resolve(any(), eq(8L))).thenThrow(forbidden);

		assertThrows(ApiException.class,
				() -> service.export(principal(7L, UserRole.MEMBER), 8L, "pdf", null));

		verifyNoInteractions(filenames.service(), assembler, pdfRenderer, docxRenderer, versions);
	}

	@Test
	void forwardsExplicitFilenameSelectionAndKeepsPreviewState() {
		Profile profile = profile(8L, 7L, 4L);
		when(access.resolve(any(), eq(8L))).thenReturn(profile);
		when(assembler.assemble(8L)).thenReturn(document());
		when(pdfRenderer.render(any())).thenReturn(new byte[] { 1 });
		filenames.returns("Explicit.pdf");

		service.export(principal(7L, UserRole.MEMBER), 8L, "pdf", 42L);

		verify(filenames.service()).resolve(profile, 42L, com.fpt.ibom.cv.model.DocumentFormat.PDF, EXPORT_TIME);
		verify(versions).markExported(profile, 4L, EXPORT_TIME);
		assertEquals(4L, profile.getVersion());
		assertFalse(!profile.isHasPreviewed());
	}

	@Test
	void filenameAssemblyAndRenderingFailuresDoNotRecordExport() {
		Profile profile = profile(8L, 7L, 4L);
		when(access.resolve(any(), eq(8L))).thenReturn(profile);

		filenames.throwFailure(new IllegalStateException("filename"));
		assertThrows(RuntimeException.class, () -> service.export(principal(7L, UserRole.MEMBER), 8L, "pdf", null));
		verifyNoInteractions(assembler, pdfRenderer, docxRenderer, versions);

		filenames.returns("Profile.pdf");
		when(assembler.assemble(8L)).thenThrow(new IllegalStateException("assembly"));
		assertThrows(RuntimeException.class, () -> service.export(principal(7L, UserRole.MEMBER), 8L, "pdf", null));
		verifyNoInteractions(pdfRenderer, docxRenderer, versions);

		doReturn(document()).when(assembler).assemble(8L);
		when(pdfRenderer.render(any())).thenThrow(new IllegalStateException("render"));
		assertThrows(RuntimeException.class, () -> service.export(principal(7L, UserRole.MEMBER), 8L, "pdf", null));
		verifyNoInteractions(versions);
		assertEquals(null, profile.getLastExportedAt());
	}

	@Test
	void staleVersionFailureDoesNotRecordTimestamp() {
		Profile profile = profile(8L, 7L, 4L);
		when(access.resolve(any(), eq(8L))).thenReturn(profile);
		when(assembler.assemble(8L)).thenReturn(document());
		when(pdfRenderer.render(any())).thenReturn(new byte[] { 1 });
		filenames.returns("Profile.pdf");
		doThrow(new ApiException(HttpStatus.CONFLICT, ErrorCode.PROFILE_VERSION_CONFLICT, "stale"))
				.when(versions).markExported(profile, 4L, EXPORT_TIME);

		ApiException exception = assertThrows(ApiException.class,
				() -> service.export(principal(7L, UserRole.MEMBER), 8L, "pdf", null));

		assertEquals(ErrorCode.PROFILE_VERSION_CONFLICT, exception.getErrorCode());
		assertEquals(null, profile.getLastExportedAt());
	}

	@Test
	void exportsAreScopedToEachResolvedProfile() {
		Profile first = profile(8L, 7L, 1L);
		Profile second = profile(9L, 7L, 3L);
		when(access.resolve(any(), eq(8L))).thenReturn(first);
		when(access.resolve(any(), eq(9L))).thenReturn(second);
		when(assembler.assemble(any())).thenReturn(document());
		when(pdfRenderer.render(any())).thenReturn(new byte[] { 1 });
		filenames.returns("Profile.pdf");

		service.export(principal(7L, UserRole.MEMBER), 8L, "pdf", null);
		service.export(principal(7L, UserRole.MEMBER), 9L, "pdf", null);

		verify(versions).markExported(first, 1L, EXPORT_TIME);
		verify(versions).markExported(second, 3L, EXPORT_TIME);
	}

	private UserPrincipal principal(Long userId, UserRole role) {
		return new UserPrincipal(userId, "user@example.com", "user", role);
	}

	private Profile profile(Long profileId, Long userId, long version) {
		UserAccount user = new UserAccount("user" + userId + "@example.com", "user" + userId, "hash", UserRole.MEMBER,
				UserStatus.ACTIVE);
		ReflectionTestUtils.setField(user, "id", userId);
		Profile profile = new Profile(user, "Profile", "First", "Last", "Engineer", BigDecimal.ONE, "Personality",
				"Summary");
		ReflectionTestUtils.setField(profile, "id", profileId);
		ReflectionTestUtils.setField(profile, "version", version);
		profile.markPreviewed();
		return profile;
	}

	private CvDocument document() {
		return new CvDocument(new CvPersonalDetails("First", "Last", "Engineer", BigDecimal.ONE, "Personality",
				"Summary"), List.of(), List.of(), List.of(), List.of(), List.of());
	}

	private static class FileNameResolutionServiceStub {
		private final com.fpt.ibom.cv.service.FileNameResolutionService service = org.mockito.Mockito
				.mock(com.fpt.ibom.cv.service.FileNameResolutionService.class);

		void returns(String fileName) {
			org.mockito.Mockito.doReturn(fileName).when(service).resolve(any(), any(), any(), any());
		}

		void throwFailure(RuntimeException exception) {
			org.mockito.Mockito.doThrow(exception).when(service).resolve(any(), any(), any(), any());
		}

		com.fpt.ibom.cv.service.FileNameResolutionService service() {
			return service;
		}
	}
}
