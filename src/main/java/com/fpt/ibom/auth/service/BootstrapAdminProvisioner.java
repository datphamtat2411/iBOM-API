package com.fpt.ibom.auth.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fpt.ibom.auth.config.BootstrapAdminProperties;
import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.exception.ApiException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BootstrapAdminProvisioner implements ApplicationRunner {

	private final UserAccountRepository userAccountRepository;
	private final UserAccountCreationService accountCreationService;
	private final BootstrapAdminProperties properties;

	public BootstrapAdminProvisioner(UserAccountRepository userAccountRepository,
			UserAccountCreationService accountCreationService, BootstrapAdminProperties properties) {
		this.userAccountRepository = userAccountRepository;
		this.accountCreationService = accountCreationService;
		this.properties = properties;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (!properties.isEnabled()) {
			return;
		}

		String email = properties.getAdminEmail();
		String username = properties.getAdminUsername();
		String password = properties.getAdminPassword();
		requireConfiguration(email, username, password);

		String normalizedEmail = normalizeEmail(email);
		String normalizedUsername = normalizeUsername(username);
		validatePassword(password);

		Optional<UserAccount> emailAccount = userAccountRepository.findByEmailIgnoreCase(normalizedEmail);
		Optional<UserAccount> usernameAccount = userAccountRepository.findByUsernameIgnoreCase(normalizedUsername);
		if (emailAccount.isPresent() || usernameAccount.isPresent()) {
			if (emailAccount.isPresent() && usernameAccount.isPresent()
					&& isSameAccount(emailAccount.get(), usernameAccount.get())) {
				return;
			}
			throw new IllegalStateException("Bootstrap admin configuration conflicts with an existing account identity");
		}

		try {
			accountCreationService.create(normalizedEmail, normalizedUsername, password, UserRole.ADMIN);
		} catch (ApiException exception) {
			throw new IllegalStateException("Bootstrap admin provisioning failed due to an account uniqueness conflict",
					exception);
		}
	}

	private void requireConfiguration(String email, String username, String password) {
		List<String> missing = new ArrayList<>();
		if (isBlank(email)) {
			missing.add("app.auth.bootstrap.admin-email");
		}
		if (isBlank(username)) {
			missing.add("app.auth.bootstrap.admin-username");
		}
		if (isBlank(password)) {
			missing.add("app.auth.bootstrap.admin-password");
		}
		if (!missing.isEmpty()) {
			throw new IllegalStateException("Bootstrap admin is enabled but required configuration is missing: "
					+ String.join(", ", missing));
		}
	}

	private String normalizeEmail(String email) {
		try {
			return accountCreationService.normalizeAndValidateEmail(email);
		} catch (ApiException exception) {
			throw invalidConfiguration("app.auth.bootstrap.admin-email", exception);
		}
	}

	private String normalizeUsername(String username) {
		try {
			return accountCreationService.normalizeAndValidateUsername(username);
		} catch (ApiException exception) {
			throw invalidConfiguration("app.auth.bootstrap.admin-username", exception);
		}
	}

	private void validatePassword(String password) {
		try {
			accountCreationService.validatePassword(password);
		} catch (ApiException exception) {
			throw invalidConfiguration("app.auth.bootstrap.admin-password", exception);
		}
	}

	private IllegalStateException invalidConfiguration(String property, ApiException cause) {
		return new IllegalStateException("Invalid bootstrap admin configuration for " + property, cause);
	}

	private boolean isSameAccount(UserAccount emailAccount, UserAccount usernameAccount) {
		return emailAccount == usernameAccount
				|| emailAccount.getId() != null && emailAccount.getId().equals(usernameAccount.getId());
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
