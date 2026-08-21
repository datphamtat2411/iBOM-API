package com.fpt.ibom.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;

import com.fpt.ibom.auth.dto.PasswordResetCodeRequest;
import com.fpt.ibom.auth.dto.PasswordResetCodeVerificationRequest;
import com.fpt.ibom.auth.dto.PasswordResetRequest;
import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.VerificationCode;
import com.fpt.ibom.auth.entity.VerificationPurpose;
import com.fpt.ibom.auth.repository.RefreshTokenRepository;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.repository.VerificationCodeRepository;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

	private static final int MAX_SENDS_PER_HOUR = 5;
	private static final int MAX_FAILED_ATTEMPTS = 5;
	private final UserAccountRepository userAccountRepository;
	private final VerificationCodeRepository verificationCodeRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final MailService mailService;
	private final SecureRandom secureRandom = new SecureRandom();

	public PasswordResetService(UserAccountRepository userAccountRepository,
			VerificationCodeRepository verificationCodeRepository, RefreshTokenRepository refreshTokenRepository,
			PasswordEncoder passwordEncoder, MailService mailService) {
		this.userAccountRepository = userAccountRepository;
		this.verificationCodeRepository = verificationCodeRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.mailService = mailService;
	}

	@Transactional
	public void requestCode(PasswordResetCodeRequest request) {
		String email = normalizeEmail(request.email());
		Instant now = Instant.now();
		if (verificationCodeRepository.countByEmailAndPurposeAndCreatedAtAfter(email, VerificationPurpose.PASSWORD_RESET,
				now.minusSeconds(3600)) >= MAX_SENDS_PER_HOUR) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.AUTH_VERIFICATION_CODE_REQUEST_LIMIT_REACHED,
					"Verification code request limit reached");
		}

		String code = "%06d".formatted(secureRandom.nextInt(1_000_000));
		verificationCodeRepository.save(new VerificationCode(email, sha256(code), VerificationPurpose.PASSWORD_RESET,
				now.plusSeconds(300), now));
		boolean eligibleForDelivery = userAccountRepository.findByEmailIgnoreCase(email).isPresent();
		mailService.sendPasswordResetCode(email, code, eligibleForDelivery);
	}

	@Transactional(noRollbackFor = ApiException.class)
	public void verifyCode(PasswordResetCodeVerificationRequest request) {
		String email = normalizeEmail(request.email());
		if (userAccountRepository.findByEmailIgnoreCase(email).isEmpty()) {
			throw invalidCode();
		}
		VerificationCode code = verificationCodeRepository.findTopByEmailAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(email,
				VerificationPurpose.PASSWORD_RESET).orElseThrow(this::invalidCode);
		if (!isValidCode(code, request.verificationCode())) {
			throw invalidCode();
		}
	}

	@Transactional(noRollbackFor = ApiException.class)
	public void resetPassword(PasswordResetRequest request) {
		String email = normalizeEmail(request.email());
		UserAccount user = userAccountRepository.findByEmailIgnoreCaseForUpdate(email).orElseThrow(this::invalidCode);
		VerificationCode code = verificationCodeRepository.findTopByEmailAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(
				email, VerificationPurpose.PASSWORD_RESET).orElseThrow(this::invalidCode);
		if (!isValidCode(code, request.verificationCode())) {
			throw invalidCode();
		}

		Instant now = Instant.now();
		user.setPasswordHash(passwordEncoder.encode(request.password()));
		code.use(now);
		refreshTokenRepository.revokeAllByUserId(user.getId(), now);
	}

	private boolean isValidCode(VerificationCode code, String submittedCode) {
		if (code.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
			return false;
		}
		if (code.getExpiresAt().isAfter(Instant.now()) && MessageDigest.isEqual(
				code.getCodeHash().getBytes(StandardCharsets.UTF_8), sha256(submittedCode).getBytes(StandardCharsets.UTF_8))) {
			return true;
		}
		code.incrementFailedAttempts();
		return false;
	}

	private ApiException invalidCode() {
		return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.AUTH_INVALID_OR_EXPIRED_VERIFICATION_CODE,
				"Invalid or expired verification code");
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
