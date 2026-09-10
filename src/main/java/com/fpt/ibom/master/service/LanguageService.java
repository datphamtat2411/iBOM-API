package com.fpt.ibom.master.service;

import com.fpt.ibom.common.PageResponse;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.master.dto.LanguageResponse;
import com.fpt.ibom.master.entity.Language;
import com.fpt.ibom.master.repository.LanguageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LanguageService {

	private final LanguageRepository languageRepository;

	public LanguageService(LanguageRepository languageRepository) {
		this.languageRepository = languageRepository;
	}

	@Transactional(readOnly = true)
	public PageResponse<LanguageResponse> list(int page, int size, String search) {
		if (page < 0 || size <= 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
					"Invalid pagination parameters");
		}

		Pageable pageable = PageRequest.of(page, size,
				Sort.by(Sort.Order.asc("name").ignoreCase(), Sort.Order.asc("id")));
		String normalizedSearch = search == null ? null : search.trim();
		Page<Language> languages = normalizedSearch == null || normalizedSearch.isEmpty()
				? languageRepository.findAll(pageable)
				: languageRepository.findByNameContainingIgnoreCase(normalizedSearch, pageable);

		return new PageResponse<>(languages.getContent().stream().map(LanguageResponse::from).toList(),
				languages.getNumber(), languages.getSize(), languages.getTotalElements(), languages.getTotalPages());
	}
}
