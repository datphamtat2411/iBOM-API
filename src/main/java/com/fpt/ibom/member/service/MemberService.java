package com.fpt.ibom.member.service;

import java.time.Instant;

import com.fpt.ibom.auth.entity.UserStatus;
import com.fpt.ibom.auth.repository.UserAccountRepository;
import com.fpt.ibom.common.PageResponse;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.member.dto.MemberSummaryResponse;
import com.fpt.ibom.member.repository.MemberSummaryProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

	private final UserAccountRepository userAccountRepository;

	public MemberService(UserAccountRepository userAccountRepository) {
		this.userAccountRepository = userAccountRepository;
	}

	@Transactional(readOnly = true)
	public PageResponse<MemberSummaryResponse> list(int page, int size, String search, UserStatus status) {
		if (page < 0 || size <= 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
					"Invalid pagination parameters");
		}

		String normalizedSearch = search == null ? null : search.trim();
		if (normalizedSearch != null && normalizedSearch.isEmpty()) {
			normalizedSearch = null;
		}
		Page<MemberSummaryProjection> members = findPage(page, size, normalizedSearch, status);
		if (members.getTotalElements() == 0) {
			return new PageResponse<>(java.util.List.of(), 0, size, 0, 0);
		}
		if (page >= members.getTotalPages()) {
			members = findPage(members.getTotalPages() - 1, size, normalizedSearch, status);
		}

		return new PageResponse<>(members.getContent().stream().map(this::toResponse).toList(), members.getNumber(),
				members.getSize(), members.getTotalElements(), members.getTotalPages());
	}

	private Page<MemberSummaryProjection> findPage(int page, int size, String search, UserStatus status) {
		Pageable pageable = PageRequest.of(page, size);
		return userAccountRepository.findMemberSummaries(status, search, pageable);
	}

	private MemberSummaryResponse toResponse(MemberSummaryProjection member) {
		Instant lastUpdatedAt = member.getAccountUpdatedAt();
		if (member.getProfileUpdatedAt() != null
				&& (lastUpdatedAt == null || member.getProfileUpdatedAt().isAfter(lastUpdatedAt))) {
			lastUpdatedAt = member.getProfileUpdatedAt();
		}
		return new MemberSummaryResponse(member.getId(), member.getUsername(), member.getEmail(), member.getStatus(),
				member.getActiveProfileCount(), lastUpdatedAt);
	}
}
