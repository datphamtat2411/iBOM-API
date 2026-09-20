package com.fpt.ibom.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;

import com.fpt.ibom.auth.dto.RegistrationCodeRequest;
import com.fpt.ibom.auth.dto.RegistrationRequest;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.VerificationCode;
import com.fpt.ibom.auth.entity.VerificationPurpose;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.repository.VerificationCodeRepository;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

	private static final int MAX_SENDS_PER_HOUR = 5;
	private static final int MAX_FAILED_ATTEMPTS = 5;
	private final UserAccountRepository userAccountRepository;
	private final VerificationCodeRepository verificationCodeRepository;
	private final UserAccountCreationService accountCreationService;
	private final MailService mailService;
	private final SecureRandom secureRandom = new SecureRandom();

	public RegistrationService(UserAccountRepository userAccountRepository,
			VerificationCodeRepository verificationCodeRepository, UserAccountCreationService accountCreationService,
			MailService mailService) {
		this.userAccountRepository = userAccountRepository;
		this.verificationCodeRepository = verificationCodeRepository;
		this.accountCreationService = accountCreationService;
		this.mailService = mailService;
	}

	@Transactional
	public void requestVerificationCode(RegistrationCodeRequest request) {
		String email = accountCreationService.normalizeAndValidateEmail(request.email());
		if (userAccountRepository.existsByEmailIgnoreCase(email)) {
			return;
		}
		Instant now = Instant.now();
		if (verificationCodeRepository.countByEmailAndPurposeAndCreatedAtAfter(email, VerificationPurpose.REGISTRATION,
				now.minusSeconds(3600)) >= MAX_SENDS_PER_HOUR) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.AUTH_VERIFICATION_CODE_REQUEST_LIMIT_REACHED,
					"Verification code request limit reached");
		}

		String code = "%06d".formatted(secureRandom.nextInt(1_000_000));
		verificationCodeRepository.save(new VerificationCode(email, sha256(code), VerificationPurpose.REGISTRATION,
				now.plusSeconds(300), now));
		mailService.sendRegistrationVerificationCode(email, code);
	}

	@Transactional(noRollbackFor = InvalidRegistrationVerificationCodeException.class)
	public void register(RegistrationRequest request) {
		UserAccountCreationService.PreparedAccount account = accountCreationService.prepare(request.email(), request.username());

		VerificationCode verificationCode = verificationCodeRepository
				.findTopByEmailAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(account.email(), VerificationPurpose.REGISTRATION)
				.orElseThrow(this::invalidVerificationCode);
		if (!isValidVerificationCode(verificationCode, request.verificationCode())) {
			throw invalidVerificationCode();
		}

		accountCreationService.create(account, request.password(), UserRole.MEMBER);
		verificationCode.use(Instant.now());
	}

	private boolean isValidVerificationCode(VerificationCode verificationCode, String submittedCode) {
		if (verificationCode.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
			return false;
		}
		if (verificationCode.getExpiresAt().isAfter(Instant.now()) && MessageDigest.isEqual(
				verificationCode.getCodeHash().getBytes(StandardCharsets.UTF_8),
				sha256(submittedCode).getBytes(StandardCharsets.UTF_8))) {
			return true;
		}
		verificationCode.incrementFailedAttempts();
		return false;
	}

	private InvalidRegistrationVerificationCodeException invalidVerificationCode() {
		return new InvalidRegistrationVerificationCodeException();
	}

	private String sha256(String value) {
		try {
			return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (java.security.NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private static final class InvalidRegistrationVerificationCodeException extends ApiException {
		private static final long serialVersionUID = 1L;

		private InvalidRegistrationVerificationCodeException() {
			super(HttpStatus.BAD_REQUEST, ErrorCode.AUTH_INVALID_OR_EXPIRED_VERIFICATION_CODE,
					"Invalid or expired verification code");
		}
	}
}
