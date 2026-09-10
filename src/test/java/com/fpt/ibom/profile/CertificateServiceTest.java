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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.profile.dto.CertificateMutationResponse;
import com.fpt.ibom.profile.dto.CertificateRequest;
import com.fpt.ibom.profile.dto.CertificateResponse;
import com.fpt.ibom.profile.dto.ProfileVersionResponse;
import com.fpt.ibom.profile.entity.Certificate;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.repository.CertificateRepository;
import com.fpt.ibom.profile.repository.ProfileRepository;
import com.fpt.ibom.profile.service.CertificateService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.OptimisticLockException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class CertificateServiceTest {

	private final CertificateRepository certificates = org.mockito.Mockito.mock(CertificateRepository.class);
	private final ProfileRepository profiles = org.mockito.Mockito.mock(ProfileRepository.class);
	private final EntityManager entityManager = org.mockito.Mockito.mock(EntityManager.class);
	private final CertificateService service = new CertificateService(certificates, profiles, entityManager);

	@Test
	void listsOwnedCertificatesInIssueDateDescendingAndIdAscendingOrder() {
		Profile profile = profile();
		Certificate newest = certificate(profile, 11L, "Newest", LocalDate.of(2024, 1, 1));
		Certificate sameDate = certificate(profile, 12L, "Same date", LocalDate.of(2024, 1, 1));
		Certificate older = certificate(profile, 13L, "Older", LocalDate.of(2023, 1, 1));
		when(profiles.findByIdAndUserIdAndDeletedAtIsNull(8L, 7L)).thenReturn(Optional.of(profile));
		when(certificates.findByProfileIdOrderByIssueDateDescIdAsc(8L))
				.thenReturn(List.of(newest, sameDate, older));

		List<CertificateResponse> result = service.list(7L, 8L);

		assertEquals(List.of("Newest", "Same date", "Older"),
				result.stream().map(CertificateResponse::certificateName).toList());
		verify(certificates).findByProfileIdOrderByIssueDateDescIdAsc(8L);
	}

	@Test
	void createsTrimmedCertificateAndInvalidatesPreview() {
		Profile profile = profile();
		ReflectionTestUtils.setField(profile, "hasPreviewed", true);
		LocalDate issueDate = LocalDate.of(2024, 1, 1);
		when(profiles.findByIdAndUserIdAndDeletedAtIsNull(8L, 7L)).thenReturn(Optional.of(profile));
		when(certificates.existsByProfileIdAndCertificateNameAndIssueDate(8L, "AWS", issueDate)).thenReturn(false);
		when(certificates.save(any(Certificate.class))).thenAnswer(invocation -> invocation.getArgument(0));
		simulateVersionIncrementOnRefresh(profile);

		CertificateMutationResponse result = service.create(7L, 8L,
				new CertificateRequest(" AWS ", issueDate, 0L));

		ArgumentCaptor<Certificate> captor = ArgumentCaptor.forClass(Certificate.class);
		verify(certificates).save(captor.capture());
		Certificate saved = captor.getValue();
		assertEquals("AWS", saved.getCertificateName());
		assertEquals(issueDate, saved.getIssueDate());
		assertFalse(profile.isHasPreviewed());
		assertEquals(1L, result.profileVersion());
		verify(certificates).existsByProfileIdAndCertificateNameAndIssueDate(8L, "AWS", issueDate);
		verify(entityManager).lock(profile, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
		verify(entityManager).refresh(profile);
	}

	@Test
	void rejectsFutureIssueDateBeforeMutation() {
		when(profiles.findByIdAndUserIdAndDeletedAtIsNull(8L, 7L)).thenReturn(Optional.of(profile()));

		ApiException exception = assertThrows(ApiException.class, () -> service.create(7L, 8L,
				new CertificateRequest("AWS", LocalDate.now().plusDays(1), 0L)));

		assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
		assertEquals(ErrorCode.CERTIFICATE_ISSUE_DATE_IN_FUTURE, exception.getErrorCode());
		verify(entityManager, never()).lock(any(), any());
		verify(certificates, never()).save(any());
	}

	@Test
	void rejectsDuplicateCertificateUsingTrimmedCasePreservingName() {
		Profile profile = profile();
		LocalDate issueDate = LocalDate.of(2024, 1, 1);
		when(profiles.findByIdAndUserIdAndDeletedAtIsNull(8L, 7L)).thenReturn(Optional.of(profile));
		when(certificates.existsByProfileIdAndCertificateNameAndIssueDate(8L, "AWS", issueDate)).thenReturn(true);

		ApiException exception = assertThrows(ApiException.class, () -> service.create(7L, 8L,
				new CertificateRequest(" AWS ", issueDate, 0L)));

		assertEquals(HttpStatus.CONFLICT, exception.getStatus());
		assertEquals(ErrorCode.CERTIFICATE_ALREADY_EXISTS, exception.getErrorCode());
		verify(certificates).existsByProfileIdAndCertificateNameAndIssueDate(8L, "AWS", issueDate);
		verify(certificates, never()).save(any());
		verify(entityManager, never()).lock(any(), any());
	}

	@Test
	void updatesCertificateThroughProfileScopedLookupAndTrimsName() {
		Profile profile = profile();
		Certificate certificate = certificate(profile, 12L, "Original", LocalDate.of(2020, 1, 1));
		LocalDate issueDate = LocalDate.of(2021, 1, 1);
		when(profiles.findByIdAndUserIdAndDeletedAtIsNull(8L, 7L)).thenReturn(Optional.of(profile));
		when(certificates.findByIdAndProfileId(12L, 8L)).thenReturn(Optional.of(certificate));
		when(certificates.existsByProfileIdAndCertificateNameAndIssueDateAndIdNot(8L, "Updated", issueDate, 12L))
				.thenReturn(false);
		when(certificates.save(certificate)).thenReturn(certificate);
		simulateVersionIncrementOnRefresh(profile);

		CertificateMutationResponse result = service.update(7L, 8L, 12L,
				new CertificateRequest(" Updated ", issueDate, 0L));

		assertEquals("Updated", certificate.getCertificateName());
		assertEquals(issueDate, certificate.getIssueDate());
		assertEquals(1L, result.profileVersion());
		verify(certificates).findByIdAndProfileId(12L, 8L);
		verify(certificates).existsByProfileIdAndCertificateNameAndIssueDateAndIdNot(8L, "Updated", issueDate, 12L);
		verify(entityManager).refresh(profile);
	}

	@Test
	void rejectsDuplicateReplacementDuringUpdate() {
		Profile profile = profile();
		Certificate certificate = certificate(profile, 12L, "Original", LocalDate.of(2020, 1, 1));
		LocalDate issueDate = LocalDate.of(2021, 1, 1);
		when(profiles.findByIdAndUserIdAndDeletedAtIsNull(8L, 7L)).thenReturn(Optional.of(profile));
		when(certificates.findByIdAndProfileId(12L, 8L)).thenReturn(Optional.of(certificate));
		when(certificates.existsByProfileIdAndCertificateNameAndIssueDateAndIdNot(8L, "Existing", issueDate, 12L))
				.thenReturn(true);

		ApiException exception = assertThrows(ApiException.class, () -> service.update(7L, 8L, 12L,
				new CertificateRequest("Existing", issueDate, 0L)));

		assertEquals(ErrorCode.CERTIFICATE_ALREADY_EXISTS, exception.getErrorCode());
		verify(certificates, never()).save(any());
		verify(entityManager, never()).lock(any(), any());
	}

	@Test
	void physicallyDeletesOwnedCertificateAndReturnsProfileVersion() {
		Profile profile = profile();
		Certificate certificate = certificate(profile, 12L, "AWS", LocalDate.of(2020, 1, 1));
		when(profiles.findByIdAndUserIdAndDeletedAtIsNull(8L, 7L)).thenReturn(Optional.of(profile));
		when(certificates.findByIdAndProfileId(12L, 8L)).thenReturn(Optional.of(certificate));
		simulateVersionIncrementOnRefresh(profile);

		ProfileVersionResponse result = service.delete(7L, 8L, 12L, 0L);

		assertEquals(1L, result.profileVersion());
		verify(certificates).delete(certificate);
		verify(certificates, never()).save(any());
		verify(entityManager).lock(profile, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
		verify(entityManager).refresh(profile);
	}

	@Test
	void rejectsForeignCertificateIdAndMissingOwnerProfile() {
		Profile profile = profile();
		when(profiles.findByIdAndUserIdAndDeletedAtIsNull(8L, 7L)).thenReturn(Optional.of(profile));
		when(certificates.findByIdAndProfileId(12L, 8L)).thenReturn(Optional.empty());

		ApiException foreign = assertThrows(ApiException.class, () -> service.delete(7L, 8L, 12L, 0L));
		assertEquals(ErrorCode.CERTIFICATE_NOT_FOUND, foreign.getErrorCode());
		verify(certificates, never()).delete(any());

		when(profiles.findByIdAndUserIdAndDeletedAtIsNull(9L, 8L)).thenReturn(Optional.empty());
		ApiException missing = assertThrows(ApiException.class, () -> service.list(8L, 9L));
		assertEquals(ErrorCode.PROFILE_NOT_FOUND, missing.getErrorCode());
	}

	@Test
	void rejectsStaleProfileVersionBeforeChildMutation() {
		Profile profile = profile();
		when(profiles.findByIdAndUserIdAndDeletedAtIsNull(8L, 7L)).thenReturn(Optional.of(profile));

		ApiException exception = assertThrows(ApiException.class, () -> service.create(7L, 8L,
				new CertificateRequest("AWS", LocalDate.of(2020, 1, 1), 1L)));

		assertEquals(ErrorCode.PROFILE_VERSION_CONFLICT, exception.getErrorCode());
		verify(entityManager, never()).lock(any(), any());
		verify(certificates, never()).save(any());
	}

	@Test
	void translatesDatabaseDuplicateAndOptimisticLockFailures() {
		Profile profile = profile();
		LocalDate issueDate = LocalDate.of(2020, 1, 1);
		when(profiles.findByIdAndUserIdAndDeletedAtIsNull(8L, 7L)).thenReturn(Optional.of(profile));
		when(certificates.existsByProfileIdAndCertificateNameAndIssueDate(8L, "AWS", issueDate)).thenReturn(false);
		when(certificates.save(any(Certificate.class))).thenAnswer(invocation -> invocation.getArgument(0));
		ConstraintViolationException violation = new ConstraintViolationException("duplicate", null,
				"uk_certificates_profile_name_issue_date");
		doThrow(new DataIntegrityViolationException("duplicate", violation)).when(entityManager).flush();

		ApiException duplicate = assertThrows(ApiException.class, () -> service.create(7L, 8L,
				new CertificateRequest("AWS", issueDate, 0L)));
		assertEquals(ErrorCode.CERTIFICATE_ALREADY_EXISTS, duplicate.getErrorCode());

		org.mockito.Mockito.reset(entityManager);
		when(profiles.findByIdAndUserIdAndDeletedAtIsNull(8L, 7L)).thenReturn(Optional.of(profile));
		when(certificates.existsByProfileIdAndCertificateNameAndIssueDate(8L, "AWS", issueDate)).thenReturn(false);
		when(certificates.save(any(Certificate.class))).thenAnswer(invocation -> invocation.getArgument(0));
		doThrow(new OptimisticLockException()).when(entityManager).flush();

		ApiException conflict = assertThrows(ApiException.class, () -> service.create(7L, 8L,
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

	private void simulateVersionIncrementOnRefresh(Profile profile) {
		org.mockito.Mockito.doAnswer(invocation -> {
			ReflectionTestUtils.setField(profile, "version", profile.getVersion() + 1);
			return null;
		}).when(entityManager).refresh(profile);
	}
}
