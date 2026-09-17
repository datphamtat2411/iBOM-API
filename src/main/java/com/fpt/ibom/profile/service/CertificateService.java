package com.fpt.ibom.profile.service;

import java.time.LocalDate;
import java.time.Clock;
import java.util.List;

import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.profile.dto.CertificateMutationResponse;
import com.fpt.ibom.profile.dto.CertificateRequest;
import com.fpt.ibom.profile.dto.CertificateResponse;
import com.fpt.ibom.profile.dto.ProfileVersionResponse;
import com.fpt.ibom.profile.entity.Certificate;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.repository.CertificateRepository;
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
	private final ProfileAccessService profileAccessService;
	private final ProfileVersionService profileVersionService;
	private final Clock clock;

	public CertificateService(CertificateRepository certificateRepository, ProfileAccessService profileAccessService,
			ProfileVersionService profileVersionService, Clock clock) {
		this.certificateRepository = certificateRepository;
		this.profileAccessService = profileAccessService;
		this.profileVersionService = profileVersionService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<CertificateResponse> list(UserPrincipal principal, Long profileId) {
		findAuthorizedProfile(principal, profileId);
		return certificateRepository.findByProfileIdOrderByIssueDateDescIdAsc(profileId).stream()
				.map(CertificateResponse::from).toList();
	}

	@Transactional
	public CertificateMutationResponse create(UserPrincipal principal, Long profileId, CertificateRequest request) {
		Profile profile = findAuthorizedProfile(principal, profileId);
		CanonicalCertificate canonical = canonicalize(request);
		checkVersion(profile, request.version());
		if (certificateRepository.existsByProfileIdAndCertificateNameAndIssueDate(profileId, canonical.certificateName(),
				canonical.issueDate())) {
			throw duplicateCertificate();
		}
		try {
			long profileVersion = profileVersionService.advance(profile);
			Certificate certificate = new Certificate(profile, canonical.certificateName(), canonical.issueDate());
			certificateRepository.saveAndFlush(certificate);
			CertificateResponse response = CertificateResponse.from(certificate);
			return new CertificateMutationResponse(response, profileVersion);
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
	public CertificateMutationResponse update(UserPrincipal principal, Long profileId, Long certificateId,
			CertificateRequest request) {
		Profile profile = findAuthorizedProfile(principal, profileId);
		Certificate certificate = certificateRepository.findByIdAndProfileId(certificateId, profileId)
				.orElseThrow(this::certificateNotFound);
		CanonicalCertificate canonical = canonicalize(request);
		checkVersion(profile, request.version());
		if (certificateRepository.existsByProfileIdAndCertificateNameAndIssueDateAndIdNot(profileId,
				canonical.certificateName(), canonical.issueDate(), certificateId)) {
			throw duplicateCertificate();
		}
		try {
			long profileVersion = profileVersionService.advance(profile);
			certificate.update(canonical.certificateName(), canonical.issueDate());
			certificateRepository.saveAndFlush(certificate);
			CertificateResponse response = CertificateResponse.from(certificate);
			return new CertificateMutationResponse(response, profileVersion);
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
	public ProfileVersionResponse delete(UserPrincipal principal, Long profileId, Long certificateId, Long version) {
		Profile profile = findAuthorizedProfile(principal, profileId);
		Certificate certificate = certificateRepository.findByIdAndProfileId(certificateId, profileId)
				.orElseThrow(this::certificateNotFound);
		checkVersion(profile, version);
		try {
			long profileVersion = profileVersionService.advance(profile);
			certificateRepository.delete(certificate);
			certificateRepository.flush();
			return new ProfileVersionResponse(profileVersion);
		} catch (OptimisticLockingFailureException | OptimisticLockException exception) {
			throw versionConflict();
		}
	}

	private Profile findAuthorizedProfile(UserPrincipal principal, Long profileId) {
		return profileAccessService.resolve(principal, profileId);
	}

	private void checkVersion(Profile profile, Long expectedVersion) {
		if (expectedVersion == null || profile.getVersion() != expectedVersion) {
			throw versionConflict();
		}
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
				String constraintName = constraintViolation.getConstraintName();
				return CERTIFICATE_UNIQUE_CONSTRAINT.equals(constraintName)
						|| constraintName != null && constraintName.endsWith("." + CERTIFICATE_UNIQUE_CONSTRAINT);
			}
			cause = cause.getCause();
		}
		return false;
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
