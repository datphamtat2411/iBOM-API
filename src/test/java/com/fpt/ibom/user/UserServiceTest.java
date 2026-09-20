package com.fpt.ibom.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.service.UserAccountCreationService;
import com.fpt.ibom.common.PageResponse;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.user.dto.UserSummaryResponse;
import com.fpt.ibom.user.dto.ManagedUserCreateRequest;
import com.fpt.ibom.user.repository.UserSummaryProjection;
import com.fpt.ibom.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class UserServiceTest {

	private final UserAccountRepository userAccountRepository = org.mockito.Mockito.mock(UserAccountRepository.class);
	private final UserAccountCreationService accountCreationService = org.mockito.Mockito.mock(UserAccountCreationService.class);
	private final UserService userService = new UserService(userAccountRepository, accountCreationService);

	@Test
	void rejectsInvalidPaginationBeforeRepositoryAccess() {
		assertThrows(ApiException.class, () -> userService.list(-1, 10, null, null));
		assertThrows(ApiException.class, () -> userService.list(0, 0, null, null));
		verifyNoInteractions(userAccountRepository);
	}

	@Test
	void trimsSearchForwardsRolesAndMapsCanonicalFiveFields() {
		UserSummaryProjection projection = projection(12L, "Alice", "alice@example.com", UserRole.ADMIN,
				UserStatus.INACTIVE);
		when(userAccountRepository.findUserSummaries(eq(List.of(UserRole.ADMIN)), eq("alice"), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(projection), org.springframework.data.domain.PageRequest.of(1, 2), 3));

		PageResponse<UserSummaryResponse> result = userService.list(1, 2, "  alice  ", List.of(UserRole.ADMIN));

		assertEquals(1, result.page());
		assertEquals(2, result.size());
		assertEquals(3, result.totalElements());
		assertEquals(2, result.totalPages());
		UserSummaryResponse user = result.content().get(0);
		assertEquals(12L, user.id());
		assertEquals("Alice", user.username());
		assertEquals("alice@example.com", user.email());
		assertEquals(UserRole.ADMIN, user.role());
		assertEquals(UserStatus.INACTIVE, user.status());
		verify(userAccountRepository).findUserSummaries(eq(List.of(UserRole.ADMIN)), eq("alice"), any(Pageable.class));
	}

	@Test
	void treatsBlankSearchAndEmptyRolesAsAbsent() {
		when(userAccountRepository.findUserSummaries(isNull(), isNull(), any(Pageable.class))).thenReturn(Page.empty());

		userService.list(0, 10, " \t ", List.of());

		verify(userAccountRepository).findUserSummaries(isNull(), isNull(), any(Pageable.class));
		verify(userAccountRepository, never()).findUserSummaries(any(), any(String.class), any(Pageable.class));
	}

	@Test
	void recoversToLastValidPage() {
		UserSummaryProjection projection = projection(12L, "Alice", "alice@example.com", UserRole.MEMBER,
				UserStatus.ACTIVE);
		when(userAccountRepository.findUserSummaries(isNull(), isNull(), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(4, 2), 5),
						new PageImpl<>(List.of(projection), org.springframework.data.domain.PageRequest.of(2, 2), 5));

		PageResponse<UserSummaryResponse> result = userService.list(4, 2, null, null);

		assertEquals(2, result.page());
		assertEquals(3, result.totalPages());
		assertEquals("Alice", result.content().get(0).username());
		ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
		InOrder order = org.mockito.Mockito.inOrder(userAccountRepository);
		order.verify(userAccountRepository, times(2)).findUserSummaries(isNull(), isNull(), pageableCaptor.capture());
		order.verifyNoMoreInteractions();
		assertEquals(List.of(4, 2), pageableCaptor.getAllValues().stream().map(Pageable::getPageNumber).toList());
		assertEquals(List.of(2, 2), pageableCaptor.getAllValues().stream().map(Pageable::getPageSize).toList());
	}

	@Test
	void normalizesEmptyDatasetToPageZero() {
		when(userAccountRepository.findUserSummaries(isNull(), isNull(), any(Pageable.class))).thenReturn(Page.empty());

		PageResponse<UserSummaryResponse> result = userService.list(9, 10, null, null);

		assertEquals(List.of(), result.content());
		assertEquals(0, result.page());
		assertEquals(10, result.size());
		assertEquals(0, result.totalElements());
		assertEquals(0, result.totalPages());
	}

	@Test
	void createsManagedUserThroughSharedAccountRulesAndMapsSummary() {
		ManagedUserCreateRequest request = new ManagedUserCreateRequest(" User@GMAIL.COM ", " managed-user ",
				"Password1!", "ADMIN");
		UserAccount account = new UserAccount("user@gmail.com", "managed-user", "bcrypt-hash", UserRole.ADMIN,
				UserStatus.ACTIVE);
		when(accountCreationService.create(request.email(), request.username(), request.password(), UserRole.ADMIN))
				.thenReturn(account);

		UserSummaryResponse result = userService.create(request);

		assertEquals("user@gmail.com", result.email());
		assertEquals("managed-user", result.username());
		assertEquals(UserRole.ADMIN, result.role());
		assertEquals(UserStatus.ACTIVE, result.status());
		verify(accountCreationService).create(request.email(), request.username(), request.password(), UserRole.ADMIN);
	}

	private UserSummaryProjection projection(Long id, String username, String email, UserRole role, UserStatus status) {
		UserSummaryProjection projection = org.mockito.Mockito.mock(UserSummaryProjection.class);
		when(projection.getId()).thenReturn(id);
		when(projection.getUsername()).thenReturn(username);
		when(projection.getEmail()).thenReturn(email);
		when(projection.getRole()).thenReturn(role);
		when(projection.getStatus()).thenReturn(status);
		return projection;
	}
}
