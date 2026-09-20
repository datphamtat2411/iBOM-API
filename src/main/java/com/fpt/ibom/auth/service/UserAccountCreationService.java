package com.fpt.ibom.auth.service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.validation.StrongPasswordValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountCreationService {

	private final UserAccountRepository userAccountRepository;
	private final PasswordEncoder passwordEncoder;
	private final Set<String> allowedDomains;

	public UserAccountCreationService(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder,
			@Value("${app.auth.registration.allowed-domains}") String allowedDomains) {
		this.userAccountRepository = userAccountRepository;
		this.passwordEncoder = passwordEncoder;
		this.allowedDomains = Arrays.stream(allowedDomains.split(","))
				.map(domain -> domain.trim().toLowerCase(Locale.ROOT))
				.filter(domain -> !domain.isEmpty())
				.collect(Collectors.toUnmodifiableSet());
	}

	public String normalizeAndValidateEmail(String email) {
		String normalizedEmail = normalizeEmail(email);
		if (normalizedEmail.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Validation failed");
		}
		ensureAllowedDomain(normalizedEmail);
		return normalizedEmail;
	}

	public String normalizeAndValidateUsername(String username) {
		String normalizedUsername = normalizeUsername(username);
		if (normalizedUsername.isBlank() || normalizedUsername.length() > 100) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Validation failed");
		}
		return normalizedUsername;
	}

	public void validatePassword(String password) {
		if (!new StrongPasswordValidator().isValid(password, null)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Validation failed");
		}
	}

	public PreparedAccount prepare(String email, String username) {
		String normalizedEmail = normalizeAndValidateEmail(email);
		String normalizedUsername = normalizeAndValidateUsername(username);
		if (userAccountRepository.existsByEmailIgnoreCase(normalizedEmail)) {
			throw new ApiException(HttpStatus.CONFLICT, ErrorCode.AUTH_EMAIL_ALREADY_REGISTERED,
					"Email is already registered");
		}
		if (userAccountRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
			throw new ApiException(HttpStatus.CONFLICT, ErrorCode.AUTH_USERNAME_ALREADY_REGISTERED,
					"Username is already registered");
		}
		return new PreparedAccount(normalizedEmail, normalizedUsername);
	}

	@Transactional
	public UserAccount create(String email, String username, String password, UserRole role) {
		return create(prepare(email, username), password, role);
	}

	@Transactional
	public UserAccount create(PreparedAccount account, String password, UserRole role) {
		if (role == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Validation failed");
		}
		validatePassword(password);
		try {
			return userAccountRepository.saveAndFlush(new UserAccount(account.email(), account.username(),
					passwordEncoder.encode(password), role, UserStatus.ACTIVE));
		} catch (DataIntegrityViolationException exception) {
			throw new ApiException(HttpStatus.CONFLICT, ErrorCode.AUTH_EMAIL_OR_USERNAME_ALREADY_REGISTERED,
					"Email or username is already registered");
		}
	}

	private String normalizeEmail(String email) {
		if (email == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Validation failed");
		}
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private String normalizeUsername(String username) {
		if (username == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Validation failed");
		}
		return username.trim();
	}

	private void ensureAllowedDomain(String email) {
		int at = email.lastIndexOf('@');
		if (at < 1 || !allowedDomains.contains(email.substring(at + 1))) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.AUTH_EMAIL_DOMAIN_NOT_ALLOWED,
					"Email domain is not allowed");
		}
	}

	public record PreparedAccount(String email, String username) {
	}
}
