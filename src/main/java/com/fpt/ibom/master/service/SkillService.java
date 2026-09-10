package com.fpt.ibom.master.service;

import com.fpt.ibom.common.PageResponse;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.master.dto.SkillResponse;
import com.fpt.ibom.master.entity.Skill;
import com.fpt.ibom.master.repository.SkillRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkillService {

	private final SkillRepository skillRepository;

	public SkillService(SkillRepository skillRepository) {
		this.skillRepository = skillRepository;
	}

	@Transactional(readOnly = true)
	public PageResponse<SkillResponse> list(int page, int size, String search) {
		if (page < 0 || size <= 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
					"Invalid pagination parameters");
		}

		Pageable pageable = PageRequest.of(page, size,
				Sort.by(Sort.Order.asc("name").ignoreCase(), Sort.Order.asc("id")));
		String normalizedSearch = search == null ? null : search.trim();
		Page<Skill> skills = normalizedSearch == null || normalizedSearch.isEmpty()
				? skillRepository.findAll(pageable)
				: skillRepository.findByNameContainingIgnoreCase(normalizedSearch, pageable);

		return new PageResponse<>(skills.getContent().stream().map(SkillResponse::from).toList(),
				skills.getNumber(), skills.getSize(), skills.getTotalElements(), skills.getTotalPages());
	}
}
