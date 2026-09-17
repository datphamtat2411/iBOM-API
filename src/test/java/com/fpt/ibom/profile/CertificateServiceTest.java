package com.fpt.ibom.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.profile.dto.CertificateMutationResponse;
import com.fpt.ibom.profile.dto.CertificateRequest;
import com.fpt.ibom.profile.dto.CertificateResponse;
import com.fpt.ibom.profile.dto.ProfileVersionResponse;
import com.fpt.ibom.profile.entity.Certificate;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.repository.CertificateRepository;
import com.fpt.ibom.profile.service.ProfileAccessService;
import com.fpt.ibom.profile.service.CertificateService;
import com.fpt.ibom.profile.service.ProfileVersionService;
import jakarta.persistence.OptimisticLockException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class CertificateServiceTest {

	private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
	private static final Instant FIXED_INSTANT = Instant.parse("2026-09-10T18:30:00Z");
	private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, BUSINESS_ZONE);
	private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 9, 11);

	private final CertificateRepository certificates = org.mockito.Mockito.mock(CertificateRepository.class);
	private final ProfileAccessService profileAccess = org.mockito.Mockito.mock(ProfileAccessService.class);
	private final ProfileVersionService profileVersions = org.mockito.Mockito.mock(ProfileVersionService.class);
	private final UserPrincipal principal = new UserPrincipal(7L, "user@example.com", "member", UserRole.MEMBER);
	private final CertificateService service = new CertificateService(certificates, profileAccess, profileVersions, FIXED_CLOCK);

	@Test
	void listsOwnedCertificatesInIssueDateDescendingAndIdAscendingOrder() {
		Profile profile = profile();
		Certificate newest = certificate(profile, 11L, "Newest", LocalDate.of(2024, 1, 1));
		Certificate sameDate = certificate(profile, 12L, "Same date", LocalDate.of(2024, 1, 1));
		Certificate older = certificate(profile, 13L, "Older", LocalDate.of(2023, 1, 1));
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(certificates.findByProfileIdOrderByIssueDateDescIdAsc(8L))
				.thenReturn(List.of(newest, sameDate, older));

		List<CertificateResponse> result = service.list(principal, 8L);

		assertEquals(List.of("Newest", "Same date", "Older"),
				result.stream().map(CertificateResponse::certificateName).toList());
		verify(certificates).findByProfileIdOrderByIssueDateDescIdAsc(8L);
	}

	@Test
	void createsTrimmedCertificateOnCurrentBusinessDateAndInvalidatesPreview() {
		Profile profile = profile();
		ReflectionTestUtils.setField(profile, "hasPreviewed", true);
		LocalDate issueDate = BUSINESS_DATE;
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(certificates.existsByProfileIdAndCertificateNameAndIssueDate(8L, "AWS", issueDate)).thenReturn(false);
		when(certificates.saveAndFlush(any(Certificate.class))).thenAnswer(invocation -> invocation.getArgument(0));
		simulateVersionIncrementOnAdvance(profile);

		CertificateMutationResponse result = service.create(principal, 8L,
				new CertificateRequest(" AWS ", issueDate, 0L));

		ArgumentCaptor<Certificate> captor = ArgumentCaptor.forClass(Certificate.class);
		verify(certificates).saveAndFlush(captor.capture());
		Certificate saved = captor.getValue();
		assertEquals("AWS", saved.getCertificateName());
		assertEquals(issueDate, saved.getIssueDate());
		assertFalse(profile.isHasPreviewed());
		assertEquals(1L, result.profileVersion());
		verify(certificates).existsByProfileIdAndCertificateNameAndIssueDate(8L, "AWS", issueDate);
		verify(profileVersions).advance(profile);
	}

	@Test
	void rejectsFutureIssueDateBeforeMutation() {
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile());

		ApiException exception = assertThrows(ApiException.class, () -> service.create(principal, 8L,
				new CertificateRequest("AWS", BUSINESS_DATE.plusDays(1), 0L)));

		assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
		assertEquals(ErrorCode.CERTIFICATE_ISSUE_DATE_IN_FUTURE, exception.getErrorCode());
		verify(profileVersions, never()).advance(any());
		verify(certificates, never()).saveAndFlush(any());
	}

	@Test
	void rejectsDuplicateCertificateUsingTrimmedCasePreservingName() {
		Profile profile = profile();
		LocalDate issueDate = LocalDate.of(2024, 1, 1);
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(certificates.existsByProfileIdAndCertificateNameAndIssueDate(8L, "AWS", issueDate)).thenReturn(true);

		ApiException exception = assertThrows(ApiException.class, () -> service.create(principal, 8L,
				new CertificateRequest(" AWS ", issueDate, 0L)));

		assertEquals(HttpStatus.CONFLICT, exception.getStatus());
		assertEquals(ErrorCode.CERTIFICATE_ALREADY_EXISTS, exception.getErrorCode());
		verify(certificates).existsByProfileIdAndCertificateNameAndIssueDate(8L, "AWS", issueDate);
		verify(certificates, never()).saveAndFlush(any());
		verify(profileVersions, never()).advance(any());
	}

	@Test
	void updatesCertificateThroughProfileScopedLookupAndTrimsName() {
		Profile profile = profile();
		Certificate certificate = certificate(profile, 12L, "Original", LocalDate.of(2020, 1, 1));
		LocalDate issueDate = LocalDate.of(2021, 1, 1);
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(certificates.findByIdAndProfileId(12L, 8L)).thenReturn(Optional.of(certificate));
		when(certificates.existsByProfileIdAndCertificateNameAndIssueDateAndIdNot(8L, "Updated", issueDate, 12L))
				.thenReturn(false);
		when(certificates.saveAndFlush(certificate)).thenReturn(certificate);
		simulateVersionIncrementOnAdvance(profile);

		CertificateMutationResponse result = service.update(principal, 8L, 12L,
				new CertificateRequest(" Updated ", issueDate, 0L));

		assertEquals("Updated", certificate.getCertificateName());
		assertEquals(issueDate, certificate.getIssueDate());
		assertEquals(1L, result.profileVersion());
		verify(certificates).findByIdAndProfileId(12L, 8L);
		verify(certificates).existsByProfileIdAndCertificateNameAndIssueDateAndIdNot(8L, "Updated", issueDate, 12L);
		verify(profileVersions).advance(profile);
	}

	@Test
	void rejectsDuplicateReplacementDuringUpdate() {
		Profile profile = profile();
		Certificate certificate = certificate(profile, 12L, "Original", LocalDate.of(2020, 1, 1));
		LocalDate issueDate = LocalDate.of(2021, 1, 1);
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(certificates.findByIdAndProfileId(12L, 8L)).thenReturn(Optional.of(certificate));
		when(certificates.existsByProfileIdAndCertificateNameAndIssueDateAndIdNot(8L, "Existing", issueDate, 12L))
				.thenReturn(true);

		ApiException exception = assertThrows(ApiException.class, () -> service.update(principal, 8L, 12L,
				new CertificateRequest("Existing", issueDate, 0L)));

		assertEquals(ErrorCode.CERTIFICATE_ALREADY_EXISTS, exception.getErrorCode());
		verify(certificates, never()).saveAndFlush(any());
		verify(profileVersions, never()).advance(any());
	}

	@Test
	void physicallyDeletesOwnedCertificateAndReturnsProfileVersion() {
		Profile profile = profile();
		Certificate certificate = certificate(profile, 12L, "AWS", LocalDate.of(2020, 1, 1));
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(certificates.findByIdAndProfileId(12L, 8L)).thenReturn(Optional.of(certificate));
		simulateVersionIncrementOnAdvance(profile);

		ProfileVersionResponse result = service.delete(principal, 8L, 12L, 0L);

		assertEquals(1L, result.profileVersion());
		verify(certificates).delete(certificate);
		verify(certificates, never()).saveAndFlush(any());
		verify(profileVersions).advance(profile);
	}

	@Test
	void rejectsForeignCertificateIdAndMissingOwnerProfile() {
		Profile profile = profile();
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(certificates.findByIdAndProfileId(12L, 8L)).thenReturn(Optional.empty());

		ApiException foreign = assertThrows(ApiException.class, () -> service.delete(principal, 8L, 12L, 0L));
		assertEquals(ErrorCode.CERTIFICATE_NOT_FOUND, foreign.getErrorCode());
		verify(certificates, never()).delete(any());

		when(profileAccess.resolve(principal, 9L)).thenThrow(new ApiException(HttpStatus.NOT_FOUND,
				ErrorCode.PROFILE_NOT_FOUND, "Profile not found"));
		ApiException missing = assertThrows(ApiException.class, () -> service.list(principal, 9L));
		assertEquals(ErrorCode.PROFILE_NOT_FOUND, missing.getErrorCode());
	}

	@Test
	void rejectsStaleProfileVersionBeforeChildMutation() {
		Profile profile = profile();
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);

		ApiException exception = assertThrows(ApiException.class, () -> service.create(principal, 8L,
				new CertificateRequest("AWS", LocalDate.of(2020, 1, 1), 1L)));

		assertEquals(ErrorCode.PROFILE_VERSION_CONFLICT, exception.getErrorCode());
		verify(profileVersions, never()).advance(any());
		verify(certificates, never()).saveAndFlush(any());
	}

	@Test
	void translatesDatabaseDuplicateAndOptimisticLockFailures() {
		Profile profile = profile();
		LocalDate issueDate = LocalDate.of(2020, 1, 1);
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(certificates.existsByProfileIdAndCertificateNameAndIssueDate(8L, "AWS", issueDate)).thenReturn(false);
		when(certificates.saveAndFlush(any(Certificate.class))).thenAnswer(invocation -> invocation.getArgument(0));
		ConstraintViolationException violation = new ConstraintViolationException("duplicate", null,
				"uk_certificates_profile_name_issue_date");
		doThrow(new DataIntegrityViolationException("duplicate", violation)).when(certificates).saveAndFlush(any());

		ApiException duplicate = assertThrows(ApiException.class, () -> service.create(principal, 8L,
				new CertificateRequest("AWS", issueDate, 0L)));
		assertEquals(ErrorCode.CERTIFICATE_ALREADY_EXISTS, duplicate.getErrorCode());

		org.mockito.Mockito.reset(certificates, profileVersions);
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(certificates.existsByProfileIdAndCertificateNameAndIssueDate(8L, "AWS", issueDate)).thenReturn(false);
		when(certificates.saveAndFlush(any(Certificate.class))).thenAnswer(invocation -> invocation.getArgument(0));
		doThrow(new OptimisticLockException()).when(profileVersions).advance(any());

		ApiException conflict = assertThrows(ApiException.class, () -> service.create(principal, 8L,
				new CertificateRequest("AWS", issueDate, 0L)));
		assertEquals(ErrorCode.PROFILE_VERSION_CONFLICT, conflict.getErrorCode());
	}

	private Profile profile() {
		return new Profile(user(), "Profile", "First", "Last", "Engineer", BigDecimal.ONE, "Personality", "Summary");
	}

	private UserAccount user() {
		return new UserAccount("user@example.com", "member", "hash", UserRole.MEMBER, UserStatus.ACTIVE);
	}

	private Certificate certificate(Profile profile, Long id, String name, LocalDate issueDate) {
		Certificate certificate = new Certificate(profile, name, issueDate);
		ReflectionTestUtils.setField(certificate, "id", id);
		return certificate;
	}

	private void simulateVersionIncrementOnAdvance(Profile profile) {
		org.mockito.Mockito.doAnswer(invocation -> {
			long nextVersion = profile.getVersion() + 1;
			ReflectionTestUtils.setField(profile, "version", nextVersion);
			ReflectionTestUtils.setField(profile, "hasPreviewed", false);
			return nextVersion;
		}).when(profileVersions).advance(profile);
	}
}
