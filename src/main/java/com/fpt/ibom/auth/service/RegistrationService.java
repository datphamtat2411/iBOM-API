package com.fpt.ibom.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.fpt.ibom.auth.dto.RegistrationCodeRequest;
import com.fpt.ibom.auth.dto.RegistrationRequest;
import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.entity.VerificationCode;
import com.fpt.ibom.auth.entity.VerificationPurpose;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.repository.VerificationCodeRepository;
import com.fpt.ibom.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

	private static final int MAX_SENDS_PER_HOUR = 5;
	private final UserAccountRepository userAccountRepository;
	private final VerificationCodeRepository verificationCodeRepository;
	private final PasswordEncoder passwordEncoder;
	private final MailService mailService;
	private final Set<String> allowedDomains;
	private final SecureRandom secureRandom = new SecureRandom();

	public RegistrationService(UserAccountRepository userAccountRepository,
			VerificationCodeRepository verificationCodeRepository, PasswordEncoder passwordEncoder, MailService mailService,
			@Value("${app.auth.registration.allowed-domains}") String allowedDomains) {
		this.userAccountRepository = userAccountRepository;
		this.verificationCodeRepository = verificationCodeRepository;
		this.passwordEncoder = passwordEncoder;
		this.mailService = mailService;
		this.allowedDomains = Arrays.stream(allowedDomains.split(","))
				.map(domain -> domain.trim().toLowerCase(Locale.ROOT))
				.filter(domain -> !domain.isEmpty())
				.collect(Collectors.toUnmodifiableSet());
	}

	@Transactional
	public void requestVerificationCode(RegistrationCodeRequest request) {
		String email = normalizeEmail(request.email());
		ensureAllowedDomain(email);
		Instant now = Instant.now();
		if (verificationCodeRepository.countByEmailAndPurposeAndCreatedAtAfter(email, VerificationPurpose.REGISTRATION,
				now.minusSeconds(3600)) >= MAX_SENDS_PER_HOUR) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Verification code request limit reached");
		}

		String code = "%06d".formatted(secureRandom.nextInt(1_000_000));
		verificationCodeRepository.save(new VerificationCode(email, sha256(code), VerificationPurpose.REGISTRATION,
				now.plusSeconds(300), now));
		mailService.sendRegistrationVerificationCode(email, code);
	}

	@Transactional
	public void register(RegistrationRequest request) {
		String email = normalizeEmail(request.email());
		String username = request.username().trim();
		ensureAllowedDomain(email);
		if (userAccountRepository.existsByEmailIgnoreCase(email)) {
			throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
		}
		if (userAccountRepository.existsByUsernameIgnoreCase(username)) {
			throw new ApiException(HttpStatus.CONFLICT, "Username is already registered");
		}

		VerificationCode verificationCode = verificationCodeRepository
				.findFirstByEmailAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(email, VerificationPurpose.REGISTRATION)
				.filter(code -> code.getExpiresAt().isAfter(Instant.now()))
				.filter(code -> MessageDigest.isEqual(code.getCodeHash().getBytes(StandardCharsets.UTF_8),
						sha256(request.verificationCode()).getBytes(StandardCharsets.UTF_8)))
				.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired verification code"));

		verificationCode.use(Instant.now());
		try {
			userAccountRepository.saveAndFlush(new UserAccount(email, username, passwordEncoder.encode(request.password()),
					UserRole.MEMBER, UserStatus.ACTIVE));
		} catch (DataIntegrityViolationException exception) {
			throw new ApiException(HttpStatus.CONFLICT, "Email or username is already registered");
		}
	}

	private void ensureAllowedDomain(String email) {
		int at = email.lastIndexOf('@');
		if (at < 1 || !allowedDomains.contains(email.substring(at + 1))) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Email domain is not allowed");
		}
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private String sha256(String value) {
		try {
			return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (java.security.NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}
}
