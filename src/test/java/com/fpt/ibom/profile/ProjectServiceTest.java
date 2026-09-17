package com.fpt.ibom.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.fpt.ibom.auth.entity.UserAccount;
import com.fpt.ibom.auth.entity.UserRole;
import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.profile.dto.ProjectMutationResponse;
import com.fpt.ibom.profile.dto.ProjectRequest;
import com.fpt.ibom.profile.dto.ProjectResponse;
import com.fpt.ibom.profile.dto.ProfileVersionResponse;
import com.fpt.ibom.profile.entity.Profile;
import com.fpt.ibom.profile.entity.Project;
import com.fpt.ibom.profile.entity.ProjectStatus;
import com.fpt.ibom.profile.repository.ProjectRepository;
import com.fpt.ibom.profile.service.ProfileAccessService;
import com.fpt.ibom.profile.service.ProjectService;
import com.fpt.ibom.profile.service.ProfileVersionService;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class ProjectServiceTest {

	private final ProjectRepository projects = org.mockito.Mockito.mock(ProjectRepository.class);
	private final ProfileAccessService profileAccess = org.mockito.Mockito.mock(ProfileAccessService.class);
	private final ProfileVersionService profileVersions = org.mockito.Mockito.mock(ProfileVersionService.class);
	private final UserPrincipal principal = new UserPrincipal(7L, "user@example.com", "member", UserRole.MEMBER);
	private final ProjectService service = new ProjectService(projects, profileAccess, profileVersions);

	@Test
	void listsProjectsInApprovedOrder() {
		Profile profile = profile();
		Project ongoing = project(profile, 11L, "Ongoing", ProjectStatus.ONGOING, LocalDate.of(2024, 1, 1), null);
		Project completed = project(profile, 12L, "Completed", ProjectStatus.COMPLETED, LocalDate.of(2020, 1, 1),
				LocalDate.of(2023, 1, 1));
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(projects.findByProfileIdInDisplayOrder(8L)).thenReturn(List.of(ongoing, completed));

		List<ProjectResponse> result = service.list(principal, 8L);

		assertEquals(List.of("Ongoing", "Completed"), result.stream().map(ProjectResponse::name).toList());
		verify(projects).findByProfileIdInDisplayOrder(8L);
	}

	@Test
	void createsCanonicalOngoingProjectWithNullableFieldsAndInvalidatesPreview() {
		Profile profile = profile();
		ReflectionTestUtils.setField(profile, "hasPreviewed", true);
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(projects.saveAndFlush(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
		simulateVersionIncrementOnAdvance(profile);

		ProjectMutationResponse result = service.create(principal, 8L,
				new ProjectRequest(" Project ", " Description ", null, LocalDate.of(2025, 1, 1), " ongoing ",
						" Engineer ", null, null, " Java, SQL ", "   ", 0L));

		ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
		verify(projects).saveAndFlush(captor.capture());
		Project saved = captor.getValue();
		assertEquals("Project", saved.getName());
		assertEquals("Description", saved.getDescription());
		assertNull(saved.getStartDate());
		assertNull(saved.getEndDate());
		assertEquals(ProjectStatus.ONGOING, saved.getStatus());
		assertEquals("Engineer", saved.getPosition());
		assertNull(saved.getTeamSize());
		assertNull(saved.getResponsibilities());
		assertEquals("Java, SQL", saved.getProgrammingLanguages());
		assertNull(saved.getTools());
		assertFalse(profile.isHasPreviewed());
		assertEquals(1L, result.profileVersion());
		verify(profileVersions).advance(profile);
	}

	@Test
	void rejectsBlankRequiredTextAfterNormalization() {
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile());

		ApiException exception = assertThrows(ApiException.class, () -> service.create(principal, 8L,
				new ProjectRequest("   ", "Description", LocalDate.of(2020, 1, 1), null, "ONGOING", "Engineer", 1,
						"Responsibilities", null, null, 0L)));

		assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
		assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
		verify(projects, never()).saveAndFlush(any());
	}

	@Test
	void rejectsZeroTeamSize() {
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile());

		ApiException exception = assertThrows(ApiException.class, () -> service.create(principal, 8L,
				request("ONGOING", LocalDate.of(2020, 1, 1), null, 0, "Responsibilities", 0L)));

		assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
		assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
		verify(projects, never()).saveAndFlush(any());
	}

	@Test
	void acceptsCompletedProjectWithoutStartDate() {
		Profile profile = profile();
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(projects.saveAndFlush(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
		simulateVersionIncrementOnAdvance(profile);

		ProjectMutationResponse result = service.create(principal, 8L,
				request("COMPLETED", null, LocalDate.of(2023, 1, 1), 1, "Responsibilities", 0L));

		ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
		verify(projects).saveAndFlush(captor.capture());
		Project saved = captor.getValue();
		assertNull(saved.getStartDate());
		assertEquals(LocalDate.of(2023, 1, 1), saved.getEndDate());
		assertEquals(ProjectStatus.COMPLETED, saved.getStatus());
		assertEquals(1L, result.profileVersion());
	}

	@Test
	void enforcesProjectStatusAndDateRules() {
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile());

		ApiException missingEndDate = assertThrows(ApiException.class, () -> service.create(principal, 8L,
				request("COMPLETED", LocalDate.of(2020, 1, 1), null, 0L)));
		assertEquals(ErrorCode.PROJECT_END_DATE_REQUIRED, missingEndDate.getErrorCode());

		ApiException invalidRange = assertThrows(ApiException.class, () -> service.create(principal, 8L,
				request("COMPLETED", LocalDate.of(2021, 1, 1), LocalDate.of(2020, 1, 1), 0L)));
		assertEquals(ErrorCode.PROJECT_DATE_RANGE_INVALID, invalidRange.getErrorCode());

		ApiException invalidStatus = assertThrows(ApiException.class, () -> service.create(principal, 8L,
				request("CANCELLED", LocalDate.of(2020, 1, 1), null, 0L)));
		assertEquals(ErrorCode.PROJECT_INVALID_STATUS, invalidStatus.getErrorCode());
		verify(profileVersions, never()).advance(any());
	}

	@Test
	void updatesCompletedProjectToOngoingAndClearsExistingEndDate() {
		Profile profile = profile();
		Project project = project(profile, 12L, "Original", ProjectStatus.COMPLETED, LocalDate.of(2020, 1, 1),
				LocalDate.of(2022, 1, 1));
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(projects.findByIdAndProfileId(12L, 8L)).thenReturn(Optional.of(project));
		when(projects.saveAndFlush(project)).thenReturn(project);
		simulateVersionIncrementOnAdvance(profile);

		ProjectMutationResponse result = service.update(principal, 8L, 12L,
				request("ONGOING", LocalDate.of(2020, 1, 1), LocalDate.of(2025, 1, 1), 0L));

		assertEquals(ProjectStatus.ONGOING, project.getStatus());
		assertNull(project.getEndDate());
		assertEquals(1L, result.profileVersion());
		verify(projects).findByIdAndProfileId(12L, 8L);
		verify(profileVersions).advance(profile);
	}

	@Test
	void physicallyDeletesOwnedProjectAndReturnsProfileVersion() {
		Profile profile = profile();
		Project project = project(profile, 12L, "Project", ProjectStatus.ONGOING, LocalDate.of(2020, 1, 1), null);
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(projects.findByIdAndProfileId(12L, 8L)).thenReturn(Optional.of(project));
		simulateVersionIncrementOnAdvance(profile);

		ProfileVersionResponse result = service.delete(principal, 8L, 12L, 0L);

		assertEquals(1L, result.profileVersion());
		verify(projects).delete(project);
		verify(projects, never()).saveAndFlush(any());
		verify(profileVersions).advance(profile);
	}

	@Test
	void rejectsForeignProjectIdsAndStaleVersions() {
		Profile profile = profile();
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(projects.findByIdAndProfileId(12L, 8L)).thenReturn(Optional.empty());

		ApiException foreign = assertThrows(ApiException.class, () -> service.delete(principal, 8L, 12L, 0L));
		assertEquals(ErrorCode.PROJECT_NOT_FOUND, foreign.getErrorCode());
		verify(projects, never()).delete(any());

		ApiException stale = assertThrows(ApiException.class, () -> service.create(principal, 8L,
				request("ONGOING", LocalDate.of(2020, 1, 1), null, 1L)));
		assertEquals(ErrorCode.PROFILE_VERSION_CONFLICT, stale.getErrorCode());
		verify(profileVersions, never()).advance(any());
	}

	@Test
	void translatesOptimisticLockFailure() {
		Profile profile = profile();
		when(profileAccess.resolve(principal, 8L)).thenReturn(profile);
		when(projects.saveAndFlush(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
		doThrow(new OptimisticLockException()).when(profileVersions).advance(any());

		ApiException exception = assertThrows(ApiException.class, () -> service.create(principal, 8L,
				request("ONGOING", LocalDate.of(2020, 1, 1), null, 0L)));

		assertEquals(ErrorCode.PROFILE_VERSION_CONFLICT, exception.getErrorCode());
	}

	private Profile profile() {
		return new Profile(user(), "Profile", "First", "Last", "Engineer", BigDecimal.ONE, "Personality", "Summary");
	}

	private UserAccount user() {
		return new UserAccount("user@example.com", "member", "hash", UserRole.MEMBER, UserStatus.ACTIVE);
	}

	private Project project(Profile profile, Long id, String name, ProjectStatus status, LocalDate startDate,
			LocalDate endDate) {
		Project project = new Project(profile, name, "Description", startDate, endDate, status, "Engineer", 1,
				"Responsibilities", "Java", "Docker");
		ReflectionTestUtils.setField(project, "id", id);
		return project;
	}

	private ProjectRequest request(String status, LocalDate startDate, LocalDate endDate, long version) {
		return request(status, startDate, endDate, 1, "Responsibilities", version);
	}

	private ProjectRequest request(String status, LocalDate startDate, LocalDate endDate, Integer teamSize,
			String responsibilities, long version) {
		return new ProjectRequest("Project", "Description", startDate, endDate, status, "Engineer", teamSize,
				responsibilities, "Java", "Docker", version);
	}

	private void simulateVersionIncrementOnAdvance(Profile profile) {
		org.mockito.Mockito.doAnswer(invocation -> {
			long nextVersion = profile.getVersion() + 1;
			ReflectionTestUtils.setField(profile, "version", nextVersion);
			ReflectionTestUtils.setField(profile, "hasPreviewed", false);
			return nextVersion;
		}).when(profileVersions).advance(profile);
	}
}
