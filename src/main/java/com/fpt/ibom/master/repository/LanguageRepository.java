package com.fpt.ibom.master.repository;

import com.fpt.ibom.master.entity.Language;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LanguageRepository extends JpaRepository<Language, Long> {

	Page<Language> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
