package com.fpt.ibom.master.service;

import com.fpt.ibom.common.PageResponse;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.master.dto.LanguageRequest;
import com.fpt.ibom.master.dto.LanguageResponse;
import com.fpt.ibom.master.entity.Language;
import com.fpt.ibom.master.repository.LanguageRepository;
import com.fpt.ibom.profile.service.ProfileLanguageService;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LanguageService {
	private static final String LANGUAGE_UNIQUE_CONSTRAINT = "uk_languages_name_ci";
	private static final String PROFILE_LANGUAGE_FOREIGN_KEY = "fk_profile_languages_language";

	private final LanguageRepository languageRepository;
	private final ProfileLanguageService profileLanguageService;

	public LanguageService(LanguageRepository languageRepository, ProfileLanguageService profileLanguageService) {
		this.languageRepository = languageRepository;
		this.profileLanguageService = profileLanguageService;
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

	@Transactional
	public LanguageResponse create(LanguageRequest request) {
		String name = canonicalize(request == null ? null : request.name());
		validateName(name);
		if (languageRepository.existsByNameIgnoreCase(name)) {
			throw languageAlreadyExists();
		}
		try {
			return LanguageResponse.from(languageRepository.saveAndFlush(new Language(name)));
		} catch (DataIntegrityViolationException exception) {
			if (isConstraintViolation(exception, LANGUAGE_UNIQUE_CONSTRAINT)) {
				throw languageAlreadyExists();
			}
			throw exception;
		}
	}

	@Transactional
	public LanguageResponse update(Long languageId, LanguageRequest request) {
		Language language = languageRepository.findById(languageId).orElseThrow(this::languageNotFound);
		String name = canonicalize(request == null ? null : request.name());
		validateName(name);
		if (languageRepository.existsByNameIgnoreCaseAndIdNot(name, languageId)) {
			throw languageAlreadyExists();
		}
		language.update(name);
		try {
			return LanguageResponse.from(languageRepository.saveAndFlush(language));
		} catch (DataIntegrityViolationException exception) {
			if (isConstraintViolation(exception, LANGUAGE_UNIQUE_CONSTRAINT)) {
				throw languageAlreadyExists();
			}
			throw exception;
		}
	}

	@Transactional
	public void delete(Long languageId) {
		Language language = languageRepository.findById(languageId).orElseThrow(this::languageNotFound);
		if (profileLanguageService.existsByLanguageId(languageId)) {
			throw languageInUse();
		}
		try {
			languageRepository.delete(language);
			languageRepository.flush();
		} catch (DataIntegrityViolationException exception) {
			if (isConstraintViolation(exception, PROFILE_LANGUAGE_FOREIGN_KEY)) {
				throw languageInUse();
			}
			throw exception;
		}
	}

	private String canonicalize(String name) {
		return name == null ? null : name.trim();
	}

	private void validateName(String name) {
		if (name == null || name.isBlank() || name.length() > 255) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Language name is invalid");
		}
	}

	private boolean isConstraintViolation(DataIntegrityViolationException exception, String expectedConstraint) {
		Throwable cause = exception;
		while (cause != null) {
			if (cause instanceof ConstraintViolationException constraintViolation) {
				String constraintName = constraintViolation.getConstraintName();
				return expectedConstraint.equals(constraintName)
						|| constraintName != null && constraintName.endsWith("." + expectedConstraint);
			}
			cause = cause.getCause();
		}
		return false;
	}

	private ApiException languageNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.LANGUAGE_NOT_FOUND, "Language not found");
	}

	private ApiException languageAlreadyExists() {
		return new ApiException(HttpStatus.CONFLICT, ErrorCode.LANGUAGE_ALREADY_EXISTS, "Language already exists");
	}

	private ApiException languageInUse() {
		return new ApiException(HttpStatus.CONFLICT, ErrorCode.LANGUAGE_IN_USE, "Language is in use");
	}
}
