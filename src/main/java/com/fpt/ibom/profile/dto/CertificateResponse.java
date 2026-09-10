package com.fpt.ibom.profile.dto;

import java.time.LocalDate;

import com.fpt.ibom.profile.entity.Certificate;

public record CertificateResponse(Long id, String certificateName, LocalDate issueDate) {

	public static CertificateResponse from(Certificate certificate) {
		return new CertificateResponse(certificate.getId(), certificate.getCertificateName(), certificate.getIssueDate());
	}
}
