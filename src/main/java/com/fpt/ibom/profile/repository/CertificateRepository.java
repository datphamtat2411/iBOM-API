package com.fpt.ibom.profile.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.fpt.ibom.profile.entity.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {

	List<Certificate> findByProfileIdOrderByIssueDateDescIdAsc(Long profileId);

	List<Certificate> findByProfileIdIn(List<Long> profileIds);

	boolean existsByProfileId(Long profileId);

	@Query("select certificate.profile.id from Certificate certificate where certificate.profile.id in :profileIds")
	List<Long> findProfileIdsByProfileIdIn(@Param("profileIds") List<Long> profileIds);

	Optional<Certificate> findByIdAndProfileId(Long certificateId, Long profileId);

	boolean existsByProfileIdAndCertificateNameAndIssueDate(Long profileId, String certificateName,
			LocalDate issueDate);

	boolean existsByProfileIdAndCertificateNameAndIssueDateAndIdNot(Long profileId, String certificateName,
			LocalDate issueDate, Long certificateId);
}
