package com.fpt.ibom.cv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fpt.ibom.MySqlIntegrationTest;
import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.cv.service.CvPreviewService;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.repository.ProfileRepository;
import com.fpt.ibom.profile.service.ProfileVersionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;

@SpringBootTest
class CvPreviewIntegrationTest extends MySqlIntegrationTest {

	@Autowired
	private CvPreviewService previewService;

	@Autowired
	private ProfileVersionService profileVersionService;

	@Autowired
	private ProfileRepository profileRepository;

	@Autowired
	private UserAccountRepository userRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void previewGeneratesPdfMarksOnlyTheRequestedProfileAndPreservesExportTimestamp() {
		UserAccount owner = saveUser(UserRole.MEMBER);
		Profile profile = saveProfile(owner, false);
		Profile otherProfile = saveProfile(owner, false);
		Instant exportedAt = Instant.parse("2026-02-03T04:05:06Z");
		profile.markExportedAt(exportedAt);
		profile = profileRepository.saveAndFlush(profile);
		long capturedVersion = profile.getVersion();

		byte[] pdf = previewService.preview(new UserPrincipal(owner.getId(), owner.getEmail(), owner.getUsername(),
				UserRole.MEMBER), profile.getId());

		assertTrue(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF-"));
		Profile previewed = profileRepository.findById(profile.getId()).orElseThrow();
		Profile untouched = profileRepository.findById(otherProfile.getId()).orElseThrow();
		assertTrue(previewed.isHasPreviewed());
		assertEquals(capturedVersion, previewed.getVersion());
		assertEquals(exportedAt, previewed.getLastExportedAt());
		assertFalse(untouched.isHasPreviewed());
	}

	@Test
	@Transactional
	void conditionalPreviewMarkRejectsAStaleAssembledVersion() {
		UserAccount owner = saveUser(UserRole.MEMBER);
		Profile profile = saveProfile(owner, false);

		assertEquals(1, profileRepository.advanceVersion(profile.getId(), 0L));
		ApiException exception = assertThrows(ApiException.class,
				() -> profileVersionService.markPreviewed(profile, 0L));

		assertEquals(ErrorCode.PROFILE_VERSION_CONFLICT, exception.getErrorCode());
		entityManager.clear();
		Profile current = profileRepository.findById(profile.getId()).orElseThrow();
		assertEquals(1L, current.getVersion());
		assertFalse(current.isHasPreviewed());
	}

	private UserAccount saveUser(UserRole role) {
		return userRepository.saveAndFlush(new UserAccount(UUID.randomUUID() + "@example.com",
				"user-" + UUID.randomUUID(), "hash", role, UserStatus.ACTIVE));
	}

	private Profile saveProfile(UserAccount owner, boolean hasPreviewed) {
		Profile profile = new Profile(owner, "Profile-" + UUID.randomUUID(), "First", "Last", "Engineer", BigDecimal.ONE,
				"Personality", "Summary");
		ReflectionTestUtils.setField(profile, "hasPreviewed", hasPreviewed);
		return profileRepository.saveAndFlush(profile);
	}
}
