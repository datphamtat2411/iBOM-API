package com.fpt.ibom.cv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.cv.model.DocumentFormat;
import com.fpt.ibom.cv.service.FileNameResolutionService;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.master.entity.FileNameFormat;
import com.fpt.ibom.master.repository.FileNameFormatRepository;
import com.fpt.ibom.profile.entity.Profile;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class FileNameResolutionServiceTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T23:30:00Z"),
			ZoneId.of("Asia/Ho_Chi_Minh"));

	private final FileNameFormatRepository formats = org.mockito.Mockito.mock(FileNameFormatRepository.class);
	private final FileNameResolutionService service = new FileNameResolutionService(formats, CLOCK);

	@Test
	void explicitFormatWinsAndDoesNotMutateProfilePreference() {
		Profile profile = profile();
		FileNameFormat preferred = format(2L, "Preferred", "{FirstName}.pdf", false);
		FileNameFormat explicit = format(3L, "Explicit", "{LastName}-{Role}-{Date}", false);
		profile.setPreferredFileNameFormat(preferred);
		when(formats.findById(3L)).thenReturn(Optional.of(explicit));

		assertEquals("Last-Engineer-20260102.pdf", service.resolve(profile, 3L, DocumentFormat.PDF));
		assertEquals(preferred, profile.getPreferredFileNameFormat());
		verify(formats).findById(3L);
		verify(formats, never()).findByIsDefaultTrue();
		verify(formats, never()).save(any(FileNameFormat.class));
		verifyNoMoreInteractions(formats);
	}

	@Test
	void invalidExplicitFormatFailsWithoutFallback() {
		Profile profile = profile();
		FileNameFormat invalid = format(3L, "Invalid", "{Unknown}", false);
		when(formats.findById(3L)).thenReturn(Optional.of(invalid));
		when(formats.findByIsDefaultTrue()).thenReturn(Optional.of(format(4L, "Default", "{LastName}", true)));

		ApiException exception = assertThrows(ApiException.class,
				() -> service.resolve(profile, 3L, DocumentFormat.PDF));

		assertEquals(ErrorCode.FILE_NAME_FORMAT_INVALID, exception.getErrorCode());
		verify(formats, never()).findByIsDefaultTrue();
	}

	@Test
	void missingExplicitFormatFailsWithoutFallback() {
		when(formats.findById(3L)).thenReturn(Optional.empty());
		when(formats.findByIsDefaultTrue()).thenReturn(Optional.of(format(4L, "Default", "{LastName}", true)));

		ApiException exception = assertThrows(ApiException.class,
				() -> service.resolve(profile(), 3L, DocumentFormat.PDF));

		assertEquals(ErrorCode.FILE_NAME_FORMAT_NOT_FOUND, exception.getErrorCode());
		verify(formats, never()).findByIsDefaultTrue();
	}

	@Test
	void preferredFormatWinsWhenExplicitSelectionIsAbsent() {
		Profile profile = profile();
		FileNameFormat preferred = format(2L, "Preferred", "{FirstName}_{Date}", false);
		profile.setPreferredFileNameFormat(preferred);
		when(formats.findByIsDefaultTrue()).thenReturn(Optional.of(format(4L, "Default", "{LastName}", true)));

		assertEquals("First_20260102.docx", service.resolve(profile, null, DocumentFormat.DOCX));
		verify(formats, never()).findByIsDefaultTrue();
	}

	@Test
	void missingPreferenceUsesTheSystemDefault() {
		when(formats.findByIsDefaultTrue()).thenReturn(Optional.of(format(4L, "Default", "{LastName}", true)));

		assertEquals("Last.pdf", service.resolve(profile(), null, DocumentFormat.PDF));
	}

	@Test
	void missingSystemDefaultFailsWithoutAHiddenPattern() {
		when(formats.findByIsDefaultTrue()).thenReturn(Optional.empty());

		ApiException exception = assertThrows(ApiException.class,
				() -> service.resolve(profile(), null, DocumentFormat.PDF));

		assertEquals(ErrorCode.FILE_NAME_FORMAT_DEFAULT_NOT_FOUND, exception.getErrorCode());
	}

	@Test
	void invalidSystemDefaultFailsClearly() {
		when(formats.findByIsDefaultTrue()).thenReturn(Optional.of(format(4L, "Default", "{Role:bad}", true)));

		ApiException exception = assertThrows(ApiException.class,
				() -> service.resolve(profile(), null, DocumentFormat.PDF));

		assertEquals(ErrorCode.FILE_NAME_FORMAT_INVALID, exception.getErrorCode());
	}

	@Test
	void resolvesAllSupportedTokensInStoredOrderAndPreservesRepeats() {
		FileNameFormat format = format(1L, "All", "{Date}_{LastName}_{FirstName}_{Role}_{LastName}", false);
		when(formats.findById(1L)).thenReturn(Optional.of(format));

		assertEquals("20260102_Last_First_Engineer_Last.docx", service.resolve(profile(), 1L, DocumentFormat.DOCX));
	}

	@Test
	void normalizesUnsafeProfileValuesWithoutTransliteration() {
		Profile profile = new Profile(user(), "Profile", "Zoë", " Nguyễn/An\n", "C++ Engineer",
				BigDecimal.ONE, null, null);
		when(formats.findById(1L)).thenReturn(Optional.of(format(1L, "Names", "{LastName}_{FirstName}_{Role}", false)));

		assertEquals("Nguyễn_An_Zoë_C++ Engineer.pdf", service.resolve(profile, 1L, DocumentFormat.PDF));
	}

	@Test
	void requiredValuesAreValidatedButUnreferencedValuesAreNotRequired() {
		Profile profile = new Profile(user(), "Profile", null, "Last", null, BigDecimal.ONE, null, null);
		when(formats.findById(1L)).thenReturn(Optional.of(format(1L, "Last only", "{LastName}", false)));
		assertEquals("Last.pdf", service.resolve(profile, 1L, DocumentFormat.PDF));

		when(formats.findById(2L)).thenReturn(Optional.of(format(2L, "First required", "{FirstName}", false)));
		ApiException exception = assertThrows(ApiException.class,
				() -> service.resolve(profile, 2L, DocumentFormat.PDF));
		assertEquals(ErrorCode.FILE_NAME_VALUE_INVALID, exception.getErrorCode());
	}

	@Test
	void valuesThatBecomeUsableOnlyAsUnsafeReplacementFail() {
		Profile profile = new Profile(user(), "Profile", "/\\", "Last", "Engineer", BigDecimal.ONE, null, null);
		when(formats.findById(1L)).thenReturn(Optional.of(format(1L, "First", "{FirstName}", false)));

		ApiException exception = assertThrows(ApiException.class,
				() -> service.resolve(profile, 1L, DocumentFormat.PDF));
		assertEquals(ErrorCode.FILE_NAME_VALUE_INVALID, exception.getErrorCode());
	}

	@Test
	void rejectsMalformedUnknownAndUnsafePersistedPatterns() {
		String[] patterns = {"", "{LastName", "LastName}", "{Unknown}", "{LastName}{FirstName", "{LastName}/cv"};
		for (int i = 0; i < patterns.length; i++) {
			long id = i;
			String pattern = patterns[i];
			when(formats.findById(id)).thenReturn(Optional.of(format(id, "Invalid", pattern, false)));

			ApiException exception = assertThrows(ApiException.class,
					() -> service.resolve(profile(), id, DocumentFormat.PDF));
			assertEquals(ErrorCode.FILE_NAME_FORMAT_INVALID, exception.getErrorCode(), pattern);
		}
	}

	private Profile profile() {
		return new Profile(user(), "Profile", "First", "Last", "Engineer", BigDecimal.ONE, null, null);
	}

	private UserAccount user() {
		return new UserAccount("user@example.com", "member", "hash", UserRole.MEMBER, UserStatus.ACTIVE);
	}

	private FileNameFormat format(Long id, String name, String pattern, boolean isDefault) {
		FileNameFormat format = new FileNameFormat(name, pattern, isDefault);
		ReflectionTestUtils.setField(format, "id", id);
		return format;
	}
}
