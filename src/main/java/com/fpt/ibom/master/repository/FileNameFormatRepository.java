package com.fpt.ibom.master.repository;

import java.util.Optional;

import com.fpt.ibom.master.entity.FileNameFormat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileNameFormatRepository extends JpaRepository<FileNameFormat, Long> {

	Optional<FileNameFormat> findByIsDefaultTrue();

	long countByIsDefaultTrue();
}
