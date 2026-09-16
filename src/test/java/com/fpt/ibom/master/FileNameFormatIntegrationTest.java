package com.fpt.ibom.master;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.UUID;

import com.fpt.ibom.MySqlIntegrationTest;
import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.master.entity.FileNameFormat;
import com.fpt.ibom.master.repository.FileNameFormatRepository;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.repository.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class FileNameFormatIntegrationTest extends MySqlIntegrationTest {

	@Autowired
	private FileNameFormatRepository fileNameFormatRepository;
	@Autowired
	private ProfileRepository profileRepository;
	@Autowired
	private UserAccountRepository userRepository;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void persistsOneUsableSystemDefaultWithAuditFields() {
		FileNameFormat systemDefault = fileNameFormatRepository.findByIsDefaultTrue().orElseThrow();

		assertNotNull(systemDefault.getId());
		assertNotNull(systemDefault.getName());
		assertNotNull(systemDefault.getPattern());
		assertEquals(true, systemDefault.isDefault());
		assertNotNull(systemDefault.getCreatedAt());
		assertNotNull(systemDefault.getUpdatedAt());
		assertEquals(0, systemDefault.getCreatedAt().getNano() % 1_000);
		assertEquals(0, systemDefault.getUpdatedAt().getNano() % 1_000);
		assertEquals(1L, fileNameFormatRepository.countByIsDefaultTrue());

		FileNameFormat custom = fileNameFormatRepository
				.saveAndFlush(new FileNameFormat("Custom " + UUID.randomUUID(), "{LastName}_{Date}", false));
		UserAccount user = userRepository.saveAndFlush(new UserAccount(UUID.randomUUID() + "@example.com",
				"member-" + UUID.randomUUID(), "hash", UserRole.MEMBER, UserStatus.ACTIVE));
		Profile profile = profileRepository.saveAndFlush(new Profile(user, "Profile-" + UUID.randomUUID(), "First",
				"Last", "Engineer", BigDecimal.ONE, null, null));
		assertNull(profile.getPreferredFileNameFormat());

		profile.setPreferredFileNameFormat(custom);
		Profile reloaded = profileRepository.saveAndFlush(profile);
		assertEquals(custom.getId(), reloaded.getPreferredFileNameFormat().getId());

		assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
				"UPDATE profiles SET preferred_file_name_format_id = ? WHERE id = ?", Long.MAX_VALUE, profile.getId()));
	}

	@Test
	void preventsMultipleSystemDefaults() {
		assertThrows(DataIntegrityViolationException.class, () -> fileNameFormatRepository.saveAndFlush(
				new FileNameFormat("Second default " + UUID.randomUUID(), "{FirstName}_{Date}", true)));
	}
}
