package com.fpt.ibom.cv;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.cv.model.CvDocument;
import com.fpt.ibom.cv.model.CvPersonalDetails;
import com.fpt.ibom.cv.service.CvDocumentAssembler;
import com.fpt.ibom.cv.service.CvPdfRenderer;
import com.fpt.ibom.cv.service.CvPreviewService;
import com.fpt.ibom.cv.service.CvProfileAccessService;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.repository.ProfileRepository;
import com.fpt.ibom.profile.service.ProfileAccessService;
import com.fpt.ibom.profile.service.ProfileVersionService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class CvPreviewServiceTest {

	private final CvProfileAccessService access = org.mockito.Mockito.mock(CvProfileAccessService.class);
	private final CvDocumentAssembler assembler = org.mockito.Mockito.mock(CvDocumentAssembler.class);
	private final CvPdfRenderer renderer = org.mockito.Mockito.mock(CvPdfRenderer.class);
	private final ProfileVersionService versions = org.mockito.Mockito.mock(ProfileVersionService.class);
	private final CvPreviewService service = new CvPreviewService(access, assembler, renderer, versions);

	@Test
	void assemblesRendersAndMarksTheCapturedProfileVersionAfterSuccessfulPreview() {
		Profile profile = profile(8L, 7L, UserRole.MEMBER, 4L);
		CvDocument document = document();
		byte[] pdf = new byte[] { 37, 80, 68, 70 };
		when(access.resolve(principal(7L, UserRole.MEMBER), 8L)).thenReturn(profile);
		when(assembler.assemble(8L)).thenReturn(document);
		when(renderer.render(document)).thenReturn(pdf);

		assertArrayEquals(pdf, service.preview(principal(7L, UserRole.MEMBER), 8L));

		InOrder order = org.mockito.Mockito.inOrder(access, assembler, renderer, versions);
		order.verify(access).resolve(principal(7L, UserRole.MEMBER), 8L);
		order.verify(assembler).assemble(8L);
		order.verify(renderer).render(document);
		order.verify(versions).markPreviewed(profile, 4L);
	}

	@Test
	void assemblyFailureLeavesPreviewStateAndRendererUntouched() {
		Profile profile = profile(8L, 7L, UserRole.MEMBER, 2L);
		RuntimeException failure = new RuntimeException("assembly failed");
		when(access.resolve(any(), org.mockito.ArgumentMatchers.eq(8L))).thenReturn(profile);
		when(assembler.assemble(8L)).thenThrow(failure);

		assertThrows(RuntimeException.class, () -> service.preview(principal(7L, UserRole.MEMBER), 8L));

		verifyNoInteractions(renderer, versions);
		assertFalse(profile.isHasPreviewed());
	}

	@Test
	void renderingFailureLeavesPreviewStateUnchanged() {
		Profile profile = profile(8L, 7L, UserRole.MEMBER, 2L);
		CvDocument document = document();
		when(access.resolve(any(), org.mockito.ArgumentMatchers.eq(8L))).thenReturn(profile);
		when(assembler.assemble(8L)).thenReturn(document);
		when(renderer.render(document)).thenThrow(new RuntimeException("render failed"));

		assertThrows(RuntimeException.class, () -> service.preview(principal(7L, UserRole.MEMBER), 8L));

		verify(assembler).assemble(8L);
		verifyNoInteractions(versions);
		assertFalse(profile.isHasPreviewed());
	}

	@Test
	void staleCapturedVersionIsRejectedWithoutChangingPreviewState() {
		Profile profile = profile(8L, 7L, UserRole.MEMBER, 2L);
		CvDocument document = document();
		when(access.resolve(any(), org.mockito.ArgumentMatchers.eq(8L))).thenReturn(profile);
		when(assembler.assemble(8L)).thenReturn(document);
		when(renderer.render(document)).thenReturn(new byte[] { 1 });
		doThrow(new ApiException(HttpStatus.CONFLICT, ErrorCode.PROFILE_VERSION_CONFLICT,
				"Profile was updated by another request")).when(versions).markPreviewed(profile, 2L);

		ApiException exception = assertThrows(ApiException.class,
				() -> service.preview(principal(7L, UserRole.MEMBER), 8L));

		assertEquals(ErrorCode.PROFILE_VERSION_CONFLICT, exception.getErrorCode());
		assertFalse(profile.isHasPreviewed());
	}

	@Test
	void alreadyPreviewedProfileCanBePreviewedAgainWithoutChangingExportTimestamp() {
		Profile profile = profile(8L, 7L, UserRole.MEMBER, 2L);
		Instant exportedAt = Instant.parse("2026-02-03T04:05:06Z");
		profile.markPreviewed();
		profile.markExportedAt(exportedAt);
		when(access.resolve(any(), org.mockito.ArgumentMatchers.eq(8L))).thenReturn(profile);
		when(assembler.assemble(8L)).thenReturn(document());
		when(renderer.render(any())).thenReturn(new byte[] { 1, 2 });

		service.preview(principal(7L, UserRole.MEMBER), 8L);

		verify(versions).markPreviewed(profile, 2L);
		assertEquals(exportedAt, profile.getLastExportedAt());
	}

	@Test
	void previewStateIsScopedToTheRequestedProfile() {
		Profile first = profile(8L, 7L, UserRole.MEMBER, 1L);
		Profile second = profile(9L, 7L, UserRole.MEMBER, 3L);
		when(access.resolve(any(), org.mockito.ArgumentMatchers.eq(8L))).thenReturn(first);
		when(access.resolve(any(), org.mockito.ArgumentMatchers.eq(9L))).thenReturn(second);
		when(assembler.assemble(any())).thenReturn(document());
		when(renderer.render(any())).thenReturn(new byte[] { 1 });

		service.preview(principal(7L, UserRole.MEMBER), 8L);
		service.preview(principal(7L, UserRole.MEMBER), 9L);

		verify(versions).markPreviewed(first, 1L);
		verify(versions).markPreviewed(second, 3L);
		verify(versions, never()).markPreviewed(first, 3L);
	}

	@Test
	void memberCannotPreviewAnotherMembersProfile() {
		ApiException notFound = new ApiException(HttpStatus.NOT_FOUND, ErrorCode.PROFILE_NOT_FOUND, "Profile not found");
		when(access.resolve(principal(7L, UserRole.MEMBER), 8L)).thenThrow(notFound);

		ApiException exception = assertThrows(ApiException.class,
				() -> service.preview(principal(7L, UserRole.MEMBER), 8L));

		assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
		assertEquals(ErrorCode.PROFILE_NOT_FOUND, exception.getErrorCode());
		verifyNoInteractions(assembler, renderer, versions);
	}

	@Test
	void activeProfileAccessAppliesOwnershipBeforeRoleDelegationAndHidesMissingProfiles() {
		Profile memberProfile = profile(8L, 7L, UserRole.MEMBER, 0L);
		Profile managerProfile = profile(9L, 8L, UserRole.MANAGER, 0L);
		Profile adminProfile = profile(10L, 9L, UserRole.ADMIN, 0L);
		ProfileRepository profiles = org.mockito.Mockito.mock(ProfileRepository.class);
		CvProfileAccessService profileAccess = new CvProfileAccessService(new ProfileAccessService(profiles));
		when(profiles.findByIdAndDeletedAtIsNull(8L)).thenReturn(Optional.of(memberProfile));
		when(profiles.findByIdAndDeletedAtIsNull(9L)).thenReturn(Optional.of(managerProfile));
		when(profiles.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(adminProfile));

		assertEquals(memberProfile, profileAccess.resolve(principal(7L, UserRole.MEMBER), 8L));
		assertEquals(managerProfile, profileAccess.resolve(principal(8L, UserRole.MANAGER), 9L));
		assertEquals(adminProfile, profileAccess.resolve(principal(9L, UserRole.ADMIN), 10L));
		assertEquals(memberProfile, profileAccess.resolve(principal(20L, UserRole.MANAGER), 8L));
		assertEquals(memberProfile, profileAccess.resolve(principal(20L, UserRole.ADMIN), 8L));
		assertEquals(HttpStatus.NOT_FOUND,
				assertThrows(ApiException.class, () -> profileAccess.resolve(principal(20L, UserRole.MEMBER), 8L)).getStatus());
		assertEquals(HttpStatus.NOT_FOUND,
				assertThrows(ApiException.class, () -> profileAccess.resolve(principal(20L, UserRole.MANAGER), 9L)).getStatus());
		assertEquals(HttpStatus.NOT_FOUND,
				assertThrows(ApiException.class, () -> profileAccess.resolve(principal(20L, UserRole.ADMIN), 10L)).getStatus());

		when(profiles.findByIdAndDeletedAtIsNull(11L)).thenReturn(Optional.empty());
		ApiException missing = assertThrows(ApiException.class,
				() -> profileAccess.resolve(principal(7L, UserRole.MEMBER), 11L));
		assertEquals(ErrorCode.PROFILE_NOT_FOUND, missing.getErrorCode());
	}

	private UserPrincipal principal(Long userId, UserRole role) {
		return new UserPrincipal(userId, "user@example.com", "user", role);
	}

	private Profile profile(Long profileId, Long userId, UserRole role, long version) {
		UserAccount user = new UserAccount("user" + userId + "@example.com", "user" + userId, "hash", role,
				UserStatus.ACTIVE);
		ReflectionTestUtils.setField(user, "id", userId);
		Profile profile = new Profile(user, "Profile", "First", "Last", "Engineer", BigDecimal.ONE, "Personality",
				"Summary");
		ReflectionTestUtils.setField(profile, "id", profileId);
		ReflectionTestUtils.setField(profile, "version", version);
		return profile;
	}

	private CvDocument document() {
		return new CvDocument(new CvPersonalDetails("First", "Last", "Engineer", BigDecimal.ONE, "Personality",
				"Summary"), List.of(), List.of(), List.of(), List.of(), List.of());
	}
}
