package com.fpt.ibom.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.profile.dto.ProfileRequest;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.repository.ProfileRepository;
import com.fpt.ibom.profile.service.ProfileService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class ProfileServiceTest {
	private final ProfileRepository profiles = mock(ProfileRepository.class);
	private final UserAccountRepository users = mock(UserAccountRepository.class);
	private final ProfileService service = new ProfileService(profiles, users);

	@Test
	void createsProfileForPrincipalUserAndInitializesPreviewState() {
		UserAccount user = new UserAccount("user@example.com", "member", "hash", UserRole.MEMBER, UserStatus.ACTIVE);
		when(profiles.existsByUserIdAndDeletedAtIsNullAndProfileNameIgnoreCase(7L, "Default")).thenReturn(false);
		when(users.findById(7L)).thenReturn(java.util.Optional.of(user));
		when(profiles.saveAndFlush(any(Profile.class))).thenAnswer(invocation -> invocation.getArgument(0));

		service.create(7L, request());

		ArgumentCaptor<Profile> captor = ArgumentCaptor.forClass(Profile.class);
		verify(profiles).saveAndFlush(captor.capture());
		assertEquals(user, captor.getValue().getUser());
		assertEquals("Default", captor.getValue().getProfileName());
		assertEquals(new BigDecimal("3.5"), captor.getValue().getYearsOfExperience());
		assertEquals(false, captor.getValue().isHasPreviewed());
	}

	@Test
	void rejectsDuplicateActiveNameWithProfileConflict() {
		when(profiles.existsByUserIdAndDeletedAtIsNullAndProfileNameIgnoreCase(7L, "Default")).thenReturn(true);

		ApiException exception = assertThrows(ApiException.class, () -> service.create(7L, request()));

		assertEquals(HttpStatus.CONFLICT, exception.getStatus());
		assertEquals(ErrorCode.PROFILE_NAME_ALREADY_EXISTS, exception.getErrorCode());
		verify(users, never()).findById(any());
	}

	private ProfileRequest request() {
		return new ProfileRequest(" Default ", "Full Name", "Engineer", "user@example.com", "0123456789",
				"Address", new BigDecimal("3.5"));
	}
}
