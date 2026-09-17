package com.fpt.ibom.master.service;

import java.util.HashSet;
import java.util.Set;

import com.fpt.ibom.common.PageResponse;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.master.dto.FileNameFormatRequest;
import com.fpt.ibom.master.dto.FileNameFormatResponse;
import com.fpt.ibom.master.entity.FileNameFormat;
import com.fpt.ibom.master.repository.FileNameFormatRepository;
import com.fpt.ibom.profile.service.ProfileFileNameFormatReferenceService;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FileNameFormatService {

	private static final String NAME_UNIQUE_CONSTRAINT = "uk_file_name_formats_name_ci";
	private static final String PROFILE_REFERENCE_CONSTRAINT = "fk_profiles_preferred_file_name_format";
	private static final Set<String> SUPPORTED_TOKENS = Set.of("LastName", "FirstName", "Role", "Date");

	private final FileNameFormatRepository fileNameFormatRepository;
	private final ProfileFileNameFormatReferenceService profileReferences;

	public FileNameFormatService(FileNameFormatRepository fileNameFormatRepository,
			ProfileFileNameFormatReferenceService profileReferences) {
		this.fileNameFormatRepository = fileNameFormatRepository;
		this.profileReferences = profileReferences;
	}

	@Transactional(readOnly = true)
	public PageResponse<FileNameFormatResponse> list(int page, int size) {
		if (page < 0 || size <= 0) {
			throw validationError("Invalid pagination parameters");
		}

		Pageable pageable = PageRequest.of(page, size,
				Sort.by(Sort.Order.asc("name").ignoreCase(), Sort.Order.asc("id")));
		Page<FileNameFormat> formats = fileNameFormatRepository.findAll(pageable);
		return new PageResponse<>(formats.getContent().stream().map(FileNameFormatResponse::from).toList(),
				formats.getNumber(), formats.getSize(), formats.getTotalElements(), formats.getTotalPages());
	}

	@Transactional
	public FileNameFormatResponse create(FileNameFormatRequest request) {
		String name = normalizeName(request.name());
		String pattern = normalizePattern(request.pattern());
		validatePattern(pattern);
		if (fileNameFormatRepository.existsByNameIgnoreCase(name)) {
			throw duplicateName();
		}

		try {
			return FileNameFormatResponse.from(fileNameFormatRepository.saveAndFlush(new FileNameFormat(name, pattern, false)));
		} catch (DataIntegrityViolationException exception) {
			if (isConstraintViolation(exception, NAME_UNIQUE_CONSTRAINT)) {
				throw duplicateName();
			}
			throw exception;
		}
	}

	@Transactional
	public FileNameFormatResponse update(Long id, FileNameFormatRequest request) {
		FileNameFormat format = find(id);
		String name = normalizeName(request.name());
		String pattern = normalizePattern(request.pattern());
		validatePattern(pattern);
		if (fileNameFormatRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
			throw duplicateName();
		}

		format.update(name, pattern);
		try {
			return FileNameFormatResponse.from(fileNameFormatRepository.saveAndFlush(format));
		} catch (DataIntegrityViolationException exception) {
			if (isConstraintViolation(exception, NAME_UNIQUE_CONSTRAINT)) {
				throw duplicateName();
			}
			throw exception;
		}
	}

	@Transactional
	public void delete(Long id) {
		FileNameFormat format = find(id);
		if (format.isDefault()) {
			throw new ApiException(HttpStatus.CONFLICT, ErrorCode.FILE_NAME_FORMAT_DEFAULT_CANNOT_DELETE,
					"The system default File Name Format cannot be deleted");
		}
		if (profileReferences.isReferenced(id)) {
			throw referencedFormat();
		}

		try {
			fileNameFormatRepository.delete(format);
			fileNameFormatRepository.flush();
		} catch (DataIntegrityViolationException exception) {
			if (isConstraintViolation(exception, PROFILE_REFERENCE_CONSTRAINT)) {
				throw referencedFormat();
			}
			throw exception;
		}
	}

	private FileNameFormat find(Long id) {
		return fileNameFormatRepository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
				ErrorCode.FILE_NAME_FORMAT_NOT_FOUND, "File Name Format not found"));
	}

	private String normalizeName(String name) {
		if (name == null || name.isBlank()) {
			throw validationError("Format Name must not be blank");
		}
		return name.trim();
	}

	private String normalizePattern(String pattern) {
		return pattern == null ? null : pattern.trim();
	}

	private void validatePattern(String pattern) {
		if (pattern == null || pattern.isBlank()) {
			throw invalidPattern("File Name Format pattern is blank");
		}

		Set<String> seenTokens = new HashSet<>();
		boolean hasPlaceholder = false;
		int index = 0;
		while (index < pattern.length()) {
			if (pattern.charAt(index) == '{') {
				int close = pattern.indexOf('}', index + 1);
				if (close < 0) {
					throw invalidPattern("File Name Format pattern has unmatched braces");
				}
				String token = pattern.substring(index + 1, close);
				if (!SUPPORTED_TOKENS.contains(token)) {
					throw invalidPattern("File Name Format pattern contains an unsupported token");
				}
				if (!seenTokens.add(token)) {
					throw invalidPattern("File Name Format pattern repeats a placeholder");
				}
				hasPlaceholder = true;
				index = close + 1;
			} else if (pattern.charAt(index) == '}') {
				throw invalidPattern("File Name Format pattern has unmatched braces");
			} else {
				int start = index;
				while (index < pattern.length() && pattern.charAt(index) != '{' && pattern.charAt(index) != '}') {
					index++;
				}
				if (containsUnsupportedLiteralCharacter(pattern.substring(start, index))) {
					throw invalidPattern("File Name Format pattern contains unsafe literal text");
				}
			}
		}

		if (!hasPlaceholder) {
			throw invalidPattern("File Name Format pattern must contain a supported placeholder");
		}
	}

	private boolean containsUnsupportedLiteralCharacter(String value) {
		for (int offset = 0; offset < value.length();) {
			int codePoint = value.codePointAt(offset);
			if (codePoint != '-' && codePoint != '_') {
				return true;
			}
			offset += Character.charCount(codePoint);
		}
		return false;
	}

	private boolean isConstraintViolation(DataIntegrityViolationException exception, String constraintName) {
		Throwable cause = exception;
		while (cause != null) {
			if (cause instanceof ConstraintViolationException violation
					&& constraintName.equals(violation.getConstraintName())) {
				return true;
			}
			cause = cause.getCause();
		}
		return exception.getMessage() != null && exception.getMessage().contains(constraintName);
	}

	private ApiException duplicateName() {
		return new ApiException(HttpStatus.CONFLICT, ErrorCode.FILE_NAME_FORMAT_NAME_ALREADY_EXISTS,
				"File Name Format name is already in use");
	}

	private ApiException referencedFormat() {
		return new ApiException(HttpStatus.CONFLICT, ErrorCode.FILE_NAME_FORMAT_REFERENCED_BY_PROFILE,
				"File Name Format is referenced by a Profile");
	}

	private ApiException invalidPattern(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.FILE_NAME_FORMAT_INVALID, message);
	}

	private ApiException validationError(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, message);
	}
}
