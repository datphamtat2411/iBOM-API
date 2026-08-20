package com.fpt.ibom.auth.service;

import com.fpt.ibom.auth.dto.AuthenticatedUser;
import com.fpt.ibom.auth.dto.ChangePasswordRequest;
import com.fpt.ibom.auth.dto.ChangeUsernameRequest;
import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.exception.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountSettingsService {

	private final UserAccountRepository userAccountRepository;
	private final PasswordEncoder passwordEncoder;

	public AccountSettingsService(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder) {
		this.userAccountRepository = userAccountRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public void changePassword(Long userId, ChangePasswordRequest request) {
		UserAccount user = account(userId);
		if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
			throw invalidCredentials();
		}
		user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
	}

	@Transactional
	public AuthenticatedUser changeUsername(Long userId, ChangeUsernameRequest request) {
		UserAccount user = account(userId);
		String username = request.username().trim();
		if (userAccountRepository.existsByUsernameIgnoreCaseAndIdNot(username, userId)) {
			throw usernameConflict();
		}

		user.setUsername(username);
		try {
			userAccountRepository.flush();
		} catch (DataIntegrityViolationException exception) {
			throw usernameConflict();
		}
		return new AuthenticatedUser(user.getId(), user.getEmail(), user.getUsername(), user.getRole());
	}

	private UserAccount account(Long userId) {
		return userAccountRepository.findById(userId).orElseThrow(this::invalidCredentials);
	}

	private ApiException invalidCredentials() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
	}

	private ApiException usernameConflict() {
		return new ApiException(HttpStatus.CONFLICT, "Username is already registered");
	}
}
