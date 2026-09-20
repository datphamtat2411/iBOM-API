package com.fpt.ibom.user.service;

import java.time.Instant;
import java.util.List;

import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.repository.RefreshTokenRepository;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.service.UserAccountCreationService;
import com.fpt.ibom.common.PageResponse;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.user.dto.UserSummaryResponse;
import com.fpt.ibom.user.dto.ManagedUserCreateRequest;
import com.fpt.ibom.user.repository.UserSummaryProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

	private final UserAccountRepository userAccountRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final UserAccountCreationService accountCreationService;

	public UserService(UserAccountRepository userAccountRepository, RefreshTokenRepository refreshTokenRepository,
			UserAccountCreationService accountCreationService) {
		this.userAccountRepository = userAccountRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.accountCreationService = accountCreationService;
	}

	@Transactional
	public UserSummaryResponse create(ManagedUserCreateRequest request) {
		UserRole role = parseManagedRole(request.role());
		UserAccount user = accountCreationService.create(request.email(), request.username(), request.password(), role);
		return new UserSummaryResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole(), user.getStatus());
	}

	@Transactional
	public UserSummaryResponse updateStatus(Long userId, Long actorId, UserStatus desiredStatus) {
		UserAccount user = userAccountRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND, "User not found"));
		if (desiredStatus == UserStatus.INACTIVE && userId.equals(actorId)) {
			throw new ApiException(HttpStatus.CONFLICT, ErrorCode.USER_SELF_DEACTIVATION_NOT_ALLOWED,
					"Users cannot deactivate their own account");
		}

		user.changeStatus(desiredStatus);
		if (desiredStatus == UserStatus.INACTIVE) {
			refreshTokenRepository.revokeAllByUserId(userId, Instant.now());
		}
		return new UserSummaryResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole(), user.getStatus());
	}

	@Transactional(readOnly = true)
	public PageResponse<UserSummaryResponse> list(int page, int size, String search, List<UserRole> roles) {
		if (page < 0 || size <= 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, com.fpt.ibom.exception.ErrorCode.VALIDATION_ERROR,
					"Invalid pagination parameters");
		}

		String normalizedSearch = search == null ? null : search.trim();
		if (normalizedSearch != null && normalizedSearch.isEmpty()) {
			normalizedSearch = null;
		}
		List<UserRole> normalizedRoles = roles == null || roles.isEmpty() ? null : List.copyOf(roles);
		Page<UserSummaryProjection> users = findPage(page, size, normalizedSearch, normalizedRoles);
		if (users.getTotalElements() == 0) {
			return new PageResponse<>(List.of(), 0, size, 0, 0);
		}
		if (page >= users.getTotalPages()) {
			users = findPage(users.getTotalPages() - 1, size, normalizedSearch, normalizedRoles);
		}

		return new PageResponse<>(users.getContent().stream().map(this::toResponse).toList(), users.getNumber(),
				users.getSize(), users.getTotalElements(), users.getTotalPages());
	}

	private Page<UserSummaryProjection> findPage(int page, int size, String search, List<UserRole> roles) {
		Pageable pageable = PageRequest.of(page, size);
		return userAccountRepository.findUserSummaries(roles, search, pageable);
	}

	private UserSummaryResponse toResponse(UserSummaryProjection user) {
		return new UserSummaryResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole(), user.getStatus());
	}

	private UserRole parseManagedRole(String role) {
		if (role == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, com.fpt.ibom.exception.ErrorCode.VALIDATION_ERROR,
					"Validation failed");
		}
		try {
			return UserRole.valueOf(role);
		} catch (IllegalArgumentException exception) {
			throw new ApiException(HttpStatus.BAD_REQUEST, com.fpt.ibom.exception.ErrorCode.VALIDATION_ERROR,
					"Validation failed");
		}
	}
}
