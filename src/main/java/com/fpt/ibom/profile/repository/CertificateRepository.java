package com.fpt.ibom.profile.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.fpt.ibom.profile.entity.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {

	List<Certificate> findByProfileIdOrderByIssueDateDescIdAsc(Long profileId);

	boolean existsByProfileId(Long profileId);

	Optional<Certificate> findByIdAndProfileId(Long certificateId, Long profileId);

	boolean existsByProfileIdAndCertificateNameAndIssueDate(Long profileId, String certificateName,
			LocalDate issueDate);

	boolean existsByProfileIdAndCertificateNameAndIssueDateAndIdNot(Long profileId, String certificateName,
			LocalDate issueDate, Long certificateId);
}
