package com.fpt.ibom.profile.service;

import java.time.LocalDate;
import java.time.Clock;
import java.util.List;

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
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.OptimisticLockException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CertificateService {
	private static final String CERTIFICATE_UNIQUE_CONSTRAINT = "uk_certificates_profile_name_issue_date";

	private final CertificateRepository certificateRepository;
	private final ProfileRepository profileRepository;
	private final EntityManager entityManager;
	private final Clock clock;

	public CertificateService(CertificateRepository certificateRepository, ProfileRepository profileRepository,
			EntityManager entityManager, Clock clock) {
		this.certificateRepository = certificateRepository;
		this.profileRepository = profileRepository;
		this.entityManager = entityManager;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<CertificateResponse> list(Long userId, Long profileId) {
		findOwnedActiveProfile(userId, profileId);
		return certificateRepository.findByProfileIdOrderByIssueDateDescIdAsc(profileId).stream()
				.map(CertificateResponse::from).toList();
	}

	@Transactional
	public CertificateMutationResponse create(Long userId, Long profileId, CertificateRequest request) {
		Profile profile = findOwnedActiveProfile(userId, profileId);
		CanonicalCertificate canonical = canonicalize(request);
		checkVersion(profile, request.version());
		if (certificateRepository.existsByProfileIdAndCertificateNameAndIssueDate(profileId, canonical.certificateName(),
				canonical.issueDate())) {
			throw duplicateCertificate();
		}
		try {
			lockAndInvalidate(profile);
			Certificate certificate = new Certificate(profile, canonical.certificateName(), canonical.issueDate());
			certificateRepository.save(certificate);
			long profileVersion = flushAndReadVersion(profile);
			return new CertificateMutationResponse(CertificateResponse.from(certificate), profileVersion);
		} catch (OptimisticLockingFailureException | OptimisticLockException exception) {
			throw versionConflict();
		} catch (DataIntegrityViolationException exception) {
			if (isCertificateUniqueConstraintViolation(exception)) {
				throw duplicateCertificate();
			}
			throw exception;
		}
	}

	@Transactional
	public CertificateMutationResponse update(Long userId, Long profileId, Long certificateId,
			CertificateRequest request) {
		Profile profile = findOwnedActiveProfile(userId, profileId);
		Certificate certificate = certificateRepository.findByIdAndProfileId(certificateId, profileId)
				.orElseThrow(this::certificateNotFound);
		CanonicalCertificate canonical = canonicalize(request);
		checkVersion(profile, request.version());
		if (certificateRepository.existsByProfileIdAndCertificateNameAndIssueDateAndIdNot(profileId,
				canonical.certificateName(), canonical.issueDate(), certificateId)) {
			throw duplicateCertificate();
		}
		try {
			lockAndInvalidate(profile);
			certificate.update(canonical.certificateName(), canonical.issueDate());
			certificateRepository.save(certificate);
			long profileVersion = flushAndReadVersion(profile);
			return new CertificateMutationResponse(CertificateResponse.from(certificate), profileVersion);
		} catch (OptimisticLockingFailureException | OptimisticLockException exception) {
			throw versionConflict();
		} catch (DataIntegrityViolationException exception) {
			if (isCertificateUniqueConstraintViolation(exception)) {
				throw duplicateCertificate();
			}
			throw exception;
		}
	}

	@Transactional
	public ProfileVersionResponse delete(Long userId, Long profileId, Long certificateId, Long version) {
		Profile profile = findOwnedActiveProfile(userId, profileId);
		Certificate certificate = certificateRepository.findByIdAndProfileId(certificateId, profileId)
				.orElseThrow(this::certificateNotFound);
		checkVersion(profile, version);
		try {
			lockAndInvalidate(profile);
			certificateRepository.delete(certificate);
			return new ProfileVersionResponse(flushAndReadVersion(profile));
		} catch (OptimisticLockingFailureException | OptimisticLockException exception) {
			throw versionConflict();
		}
	}

	private Profile findOwnedActiveProfile(Long userId, Long profileId) {
		return profileRepository.findByIdAndUserIdAndDeletedAtIsNull(profileId, userId)
				.orElseThrow(this::profileNotFound);
	}

	private void checkVersion(Profile profile, Long expectedVersion) {
		if (expectedVersion == null || profile.getVersion() != expectedVersion) {
			throw versionConflict();
		}
	}

	private void lockAndInvalidate(Profile profile) {
		entityManager.lock(profile, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
		profile.invalidatePreview();
	}

	private long flushAndReadVersion(Profile profile) {
		long versionBeforeFlush = profile.getVersion();
		entityManager.flush();
		entityManager.refresh(profile);
		return Math.max(profile.getVersion(), versionBeforeFlush + 1);
	}

	private CanonicalCertificate canonicalize(CertificateRequest request) {
		if (request.issueDate().isAfter(LocalDate.now(clock))) {
			throw futureIssueDate();
		}
		return new CanonicalCertificate(request.certificateName().trim(), request.issueDate());
	}

	private boolean isCertificateUniqueConstraintViolation(DataIntegrityViolationException exception) {
		Throwable cause = exception;
		while (cause != null) {
			if (cause instanceof ConstraintViolationException constraintViolation) {
				return CERTIFICATE_UNIQUE_CONSTRAINT.equals(constraintViolation.getConstraintName());
			}
			cause = cause.getCause();
		}
		return false;
	}

	private ApiException profileNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.PROFILE_NOT_FOUND, "Profile not found");
	}

	private ApiException certificateNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.CERTIFICATE_NOT_FOUND, "Certificate not found");
	}

	private ApiException duplicateCertificate() {
		return new ApiException(HttpStatus.CONFLICT, ErrorCode.CERTIFICATE_ALREADY_EXISTS,
				"Certificate is already assigned to this Profile");
	}

	private ApiException futureIssueDate() {
		return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.CERTIFICATE_ISSUE_DATE_IN_FUTURE,
				"Certificate issue date must not be in the future");
	}

	private ApiException versionConflict() {
		return new ApiException(HttpStatus.CONFLICT, ErrorCode.PROFILE_VERSION_CONFLICT,
				"Profile was updated by another request");
	}

	private record CanonicalCertificate(String certificateName, LocalDate issueDate) {
	}
}
