package com.fpt.ibom.cv.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fpt.ibom.cv.model.DocumentFormat;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.master.entity.FileNameFormat;
import com.fpt.ibom.master.repository.FileNameFormatRepository;
import com.fpt.ibom.profile.entity.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class FileNameResolutionService {

	private static final Set<String> SUPPORTED_TOKENS = Set.of("LastName", "FirstName", "Role", "Date");
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

	private final FileNameFormatRepository fileNameFormats;
	private final Clock clock;

	public FileNameResolutionService(FileNameFormatRepository fileNameFormats, Clock clock) {
		this.fileNameFormats = fileNameFormats;
		this.clock = clock;
	}

	public String resolve(Profile profile, Long explicitFileNameFormatId, DocumentFormat documentFormat) {
		if (profile == null) {
			throw valueError("Profile is required");
		}
		if (documentFormat == null) {
			throw formatError("Document format is required");
		}

		FileNameFormat format = selectFormat(profile, explicitFileNameFormatId);
		List<Part> parts = parsePattern(format.getPattern());
		StringBuilder fileName = new StringBuilder();
		for (Part part : parts) {
			if (part.token() == null) {
				fileName.append(part.value());
			} else {
				fileName.append(resolveToken(part.token(), profile));
			}
		}

		String resolvedFileName = fileName.toString().trim();
		if (resolvedFileName.isEmpty() || resolvedFileName.equals(".") || resolvedFileName.equals("..")
				|| resolvedFileName.indexOf('/') >= 0 || resolvedFileName.indexOf('\\') >= 0) {
			throw formatError("Filename pattern produces an unusable filename");
		}
		return resolvedFileName + documentFormat.getExtension();
	}

	private FileNameFormat selectFormat(Profile profile, Long explicitFileNameFormatId) {
		if (explicitFileNameFormatId != null) {
			return fileNameFormats.findById(explicitFileNameFormatId)
					.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.FILE_NAME_FORMAT_NOT_FOUND,
							"File Name Format not found"));
		}

		if (profile.getPreferredFileNameFormat() != null) {
			return profile.getPreferredFileNameFormat();
		}

		return fileNameFormats.findByIsDefaultTrue()
				.orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
						ErrorCode.FILE_NAME_FORMAT_DEFAULT_NOT_FOUND, "Usable system default File Name Format not found"));
	}

	private List<Part> parsePattern(String pattern) {
		if (pattern == null || pattern.isBlank()) {
			throw formatError("File Name Format pattern is blank");
		}

		List<Part> parts = new ArrayList<>();
		int index = 0;
		while (index < pattern.length()) {
			if (pattern.charAt(index) == '{') {
				int close = pattern.indexOf('}', index + 1);
				if (close < 0) {
					throw formatError("File Name Format pattern has unmatched braces");
				}
				String token = pattern.substring(index + 1, close);
				if (token.isEmpty() || token.indexOf('{') >= 0 || token.indexOf('}') >= 0
						|| !SUPPORTED_TOKENS.contains(token)) {
					throw formatError("File Name Format pattern contains an unsupported token");
				}
				parts.add(new Part(token, null));
				index = close + 1;
			} else if (pattern.charAt(index) == '}') {
				throw formatError("File Name Format pattern has unmatched braces");
			} else {
				int start = index;
				while (index < pattern.length() && pattern.charAt(index) != '{' && pattern.charAt(index) != '}') {
					index++;
				}
				String literal = pattern.substring(start, index);
				if (containsUnsafeFilenameCharacter(literal)) {
					throw formatError("File Name Format pattern contains unsafe literal text");
				}
				parts.add(new Part(null, literal));
			}
		}
		return parts;
	}

	private String resolveToken(String token, Profile profile) {
		return switch (token) {
		case "LastName" -> normalizeProfileValue(profile.getLastName(), "LastName");
		case "FirstName" -> normalizeProfileValue(profile.getFirstName(), "FirstName");
		case "Role" -> normalizeProfileValue(profile.getJobTitle(), "Role");
		case "Date" -> DATE_FORMAT.format(LocalDate.now(clock));
		default -> throw formatError("File Name Format pattern contains an unsupported token");
		};
	}

	private String normalizeProfileValue(String value, String component) {
		if (value == null || value.trim().isEmpty()) {
			throw valueError("Profile " + component + " is required by the File Name Format");
		}

		String trimmed = value.trim();
		StringBuilder normalized = new StringBuilder(trimmed.length());
		boolean meaningfulCharacter = false;
		for (int offset = 0; offset < trimmed.length();) {
			int codePoint = trimmed.codePointAt(offset);
			if (isUnsafeFilenameCharacter(codePoint)) {
				normalized.append('_');
			} else {
				normalized.appendCodePoint(codePoint);
				if (!Character.isWhitespace(codePoint) && codePoint != '.' && codePoint != '-'
						&& codePoint != '_') {
					meaningfulCharacter = true;
				}
			}
			offset += Character.charCount(codePoint);
		}

		String result = normalized.toString().trim();
		if (result.isEmpty() || !meaningfulCharacter) {
			throw valueError("Profile " + component + " is unusable in a filename");
		}
		return result;
	}

	private boolean containsUnsafeFilenameCharacter(String value) {
		for (int offset = 0; offset < value.length();) {
			int codePoint = value.codePointAt(offset);
			if (isUnsafeFilenameCharacter(codePoint)) {
				return true;
			}
			offset += Character.charCount(codePoint);
		}
		return false;
	}

	private boolean isUnsafeFilenameCharacter(int codePoint) {
		return Character.isISOControl(codePoint) || codePoint == '/' || codePoint == '\\' || codePoint == ':'
				|| codePoint == '*' || codePoint == '?' || codePoint == '"' || codePoint == '<' || codePoint == '>'
				|| codePoint == '|';
	}

	private ApiException formatError(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.FILE_NAME_FORMAT_INVALID, message);
	}

	private ApiException valueError(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.FILE_NAME_VALUE_INVALID, message);
	}

	private record Part(String token, String value) {
	}
}
