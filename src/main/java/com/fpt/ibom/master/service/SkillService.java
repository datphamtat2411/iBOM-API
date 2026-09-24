package com.fpt.ibom.master.service;

import com.fpt.ibom.common.PageResponse;
import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.master.dto.SkillRequest;
import com.fpt.ibom.master.dto.SkillResponse;
import com.fpt.ibom.master.entity.Skill;
import com.fpt.ibom.master.entity.SkillCategory;
import com.fpt.ibom.master.repository.SkillCategoryRepository;
import com.fpt.ibom.master.repository.SkillRepository;
import com.fpt.ibom.profile.repository.ProfileSkillRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkillService {
	private static final String SKILL_NAME_UNIQUE_CONSTRAINT = "uk_skills_name_ci";
	private static final String PROFILE_SKILL_FOREIGN_KEY = "fk_profile_skills_skill";

	private final SkillRepository skillRepository;
	private final SkillCategoryRepository categoryRepository;
	private final ProfileSkillRepository profileSkillRepository;

	public SkillService(SkillRepository skillRepository, SkillCategoryRepository categoryRepository,
			ProfileSkillRepository profileSkillRepository) {
		this.skillRepository = skillRepository;
		this.categoryRepository = categoryRepository;
		this.profileSkillRepository = profileSkillRepository;
	}

	@Transactional(readOnly = true)
	public PageResponse<SkillResponse> list(int page, int size, String search, Long categoryId) {
		if (page < 0 || size <= 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
					"Invalid pagination parameters");
		}

		Pageable pageable = PageRequest.of(page, size,
				Sort.by(Sort.Order.asc("name").ignoreCase(), Sort.Order.asc("id")));
		String normalizedSearch = search == null ? null : search.trim();
		if (categoryId != null) {
			findCategory(categoryId);
		}
		Page<Skill> skills;
		if (categoryId == null) {
			skills = normalizedSearch == null || normalizedSearch.isEmpty()
					? skillRepository.findAll(pageable)
					: skillRepository.findByNameContainingIgnoreCase(normalizedSearch, pageable);
		} else {
			skills = normalizedSearch == null || normalizedSearch.isEmpty()
					? skillRepository.findByCategoryId(categoryId, pageable)
					: skillRepository.findByCategoryIdAndNameContainingIgnoreCase(categoryId, normalizedSearch, pageable);
		}

		return new PageResponse<>(skills.getContent().stream().map(SkillResponse::from).toList(),
				skills.getNumber(), skills.getSize(), skills.getTotalElements(), skills.getTotalPages());
	}

	@Transactional
	public SkillResponse create(SkillRequest request) {
		CanonicalSkill canonical = canonicalize(request);
		SkillCategory category = findCategory(canonical.categoryId());
		if (skillRepository.existsByNameIgnoreCase(canonical.name())) {
			throw skillNameAlreadyExists();
		}

		try {
			return SkillResponse.from(skillRepository.saveAndFlush(new Skill(canonical.name(), category)));
		} catch (DataIntegrityViolationException exception) {
			if (isConstraintViolation(exception, SKILL_NAME_UNIQUE_CONSTRAINT)) {
				throw skillNameAlreadyExists();
			}
			throw exception;
		}
	}

	@Transactional
	public SkillResponse update(Long skillId, SkillRequest request) {
		Skill skill = findSkill(skillId);
		CanonicalSkill canonical = canonicalize(request);
		SkillCategory category = findCategory(canonical.categoryId());
		if (skillRepository.existsByNameIgnoreCaseAndIdNot(canonical.name(), skillId)) {
			throw skillNameAlreadyExists();
		}

		try {
			skill.update(canonical.name(), category);
			return SkillResponse.from(skillRepository.saveAndFlush(skill));
		} catch (DataIntegrityViolationException exception) {
			if (isConstraintViolation(exception, SKILL_NAME_UNIQUE_CONSTRAINT)) {
				throw skillNameAlreadyExists();
			}
			throw exception;
		}
	}

	@Transactional
	public void delete(Long skillId) {
		Skill skill = findSkill(skillId);
		if (profileSkillRepository.existsBySkillId(skillId)) {
			throw skillReferencedByProfiles();
		}

		try {
			skillRepository.delete(skill);
			skillRepository.flush();
		} catch (DataIntegrityViolationException exception) {
			if (isConstraintViolation(exception, PROFILE_SKILL_FOREIGN_KEY)) {
				throw skillReferencedByProfiles();
			}
			throw exception;
		}
	}

	private CanonicalSkill canonicalize(SkillRequest request) {
		if (request == null || request.name() == null || request.name().trim().isEmpty()
				|| request.name().trim().length() > 255 || request.categoryId() == null || request.categoryId() <= 0) {
			throw validationFailed();
		}
		return new CanonicalSkill(request.name().trim(), request.categoryId());
	}

	private Skill findSkill(Long skillId) {
		return skillRepository.findById(skillId).orElseThrow(this::skillNotFound);
	}

	private SkillCategory findCategory(Long categoryId) {
		return categoryRepository.findById(categoryId).orElseThrow(this::categoryNotFound);
	}

	private boolean isConstraintViolation(DataIntegrityViolationException exception, String expectedConstraint) {
		Throwable cause = exception;
		while (cause != null) {
			if (cause instanceof ConstraintViolationException constraintViolation) {
				String constraintName = constraintViolation.getConstraintName();
				return expectedConstraint.equals(constraintName)
						|| constraintName != null && constraintName.endsWith("." + expectedConstraint);
			}
			cause = cause.getCause();
		}
		return false;
	}

	private ApiException validationFailed() {
		return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Validation failed");
	}

	private ApiException skillNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.SKILL_NOT_FOUND, "Skill not found");
	}

	private ApiException categoryNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.SKILL_CATEGORY_NOT_FOUND, "Skill Category not found");
	}

	private ApiException skillNameAlreadyExists() {
		return new ApiException(HttpStatus.CONFLICT, ErrorCode.SKILL_NAME_ALREADY_EXISTS,
				"Skill name already exists");
	}

	private ApiException skillReferencedByProfiles() {
		return new ApiException(HttpStatus.CONFLICT, ErrorCode.SKILL_REFERENCED_BY_PROFILES,
				"Skill is referenced by Profile Skill data");
	}

	private record CanonicalSkill(String name, Long categoryId) {
	}
}
