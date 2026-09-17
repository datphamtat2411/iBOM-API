package com.fpt.ibom.master.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.fpt.ibom.exception.ApiException;
import com.fpt.ibom.exception.ErrorCode;
import com.fpt.ibom.master.dto.SeniorityMutationRequest;
import com.fpt.ibom.master.dto.SeniorityResponse;
import com.fpt.ibom.master.entity.Seniority;
import com.fpt.ibom.master.repository.SeniorityRepository;
import com.fpt.ibom.profile.repository.ProfileSkillRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeniorityService {
	private static final String NAME_UNIQUE_CONSTRAINT = "uk_seniorities_name_ci";

	private final SeniorityRepository seniorityRepository;
	private final ProfileSkillRepository profileSkillRepository;

	public SeniorityService(SeniorityRepository seniorityRepository, ProfileSkillRepository profileSkillRepository) {
		this.seniorityRepository = seniorityRepository;
		this.profileSkillRepository = profileSkillRepository;
	}

	@Transactional(readOnly = true)
	public List<SeniorityResponse> list() {
		return seniorityRepository.findAllByOrderByFromExperienceAscIdAsc().stream()
				.map(SeniorityResponse::from).toList();
	}

	@Transactional
	public SeniorityResponse create(SeniorityMutationRequest request) {
		CanonicalRange candidate = canonicalize(request);
		seniorityRepository.lockMutationSection();
		if (seniorityRepository.existsByNameIgnoreCase(candidate.name())) {
			throw duplicateName();
		}
		validateRanges(candidate, null);
		try {
			return SeniorityResponse.from(seniorityRepository.saveAndFlush(
					new Seniority(candidate.name(), candidate.fromExperience(), candidate.toExperience())));
		} catch (DataIntegrityViolationException exception) {
			if (isNameConstraintViolation(exception)) {
				throw duplicateName();
			}
			throw exception;
		}
	}

	@Transactional
	public SeniorityResponse update(Long seniorityId, SeniorityMutationRequest request) {
		seniorityRepository.lockMutationSection();
		Seniority seniority = seniorityRepository.findById(seniorityId).orElseThrow(this::notFound);
		CanonicalRange candidate = canonicalize(request);
		if (seniorityRepository.existsByNameIgnoreCaseAndIdNot(candidate.name(), seniorityId)) {
			throw duplicateName();
		}
		validateRanges(candidate, seniorityId);
		seniority.update(candidate.name(), candidate.fromExperience(), candidate.toExperience());
		try {
			return SeniorityResponse.from(seniorityRepository.saveAndFlush(seniority));
		} catch (DataIntegrityViolationException exception) {
			if (isNameConstraintViolation(exception)) {
				throw duplicateName();
			}
			throw exception;
		}
	}

	@Transactional
	public void delete(Long seniorityId) {
		Seniority seniority = seniorityRepository.findById(seniorityId).orElseThrow(this::notFound);
		boolean inUse = seniority.getToExperience() == null
				? profileSkillRepository.existsByExperienceYearsGreaterThanEqual(seniority.getFromExperience())
				: profileSkillRepository.existsByExperienceYearsGreaterThanEqualAndExperienceYearsLessThan(
						seniority.getFromExperience(), seniority.getToExperience());
		if (inUse) {
			throw inUse();
		}
		seniorityRepository.delete(seniority);
		seniorityRepository.flush();
	}

	private CanonicalRange canonicalize(SeniorityMutationRequest request) {
		if (request == null || request.name() == null || request.name().isBlank() || request.fromExperience() == null) {
			throw validationError();
		}
		String name = request.name().trim();
		BigDecimal from = request.fromExperience();
		BigDecimal to = request.toExperience();
		if (from.compareTo(BigDecimal.ZERO) < 0 || to != null && (to.compareTo(BigDecimal.ZERO) < 0
				|| to.compareTo(from) <= 0)) {
			throw validationError();
		}
		return new CanonicalRange(name, from, to);
	}

	private void validateRanges(CanonicalRange candidate, Long excludedId) {
		List<CanonicalRange> ranges = new ArrayList<>();
		for (Seniority seniority : seniorityRepository.findAllByOrderByFromExperienceAscIdAsc()) {
			if (!seniority.getId().equals(excludedId)) {
				ranges.add(new CanonicalRange(seniority.getName(), seniority.getFromExperience(), seniority.getToExperience()));
			}
		}
		ranges.add(candidate);
		for (int i = 0; i < ranges.size(); i++) {
			for (int j = i + 1; j < ranges.size(); j++) {
				if (overlaps(ranges.get(i), ranges.get(j))) {
					throw rangeConflict();
				}
			}
		}
		BigDecimal highestFiniteUpper = ranges.stream().map(CanonicalRange::toExperience)
				.filter(value -> value != null).max(BigDecimal::compareTo).orElse(null);
		if (highestFiniteUpper != null) {
			for (CanonicalRange range : ranges) {
				if (range.toExperience() == null && range.fromExperience().compareTo(highestFiniteUpper) < 0) {
					throw rangeConflict();
				}
			}
		}
	}

	private boolean overlaps(CanonicalRange left, CanonicalRange right) {
		return lessThan(left.fromExperience(), right.toExperience())
				&& lessThan(right.fromExperience(), left.toExperience());
	}

	private boolean lessThan(BigDecimal value, BigDecimal upperBound) {
		return upperBound == null || value.compareTo(upperBound) < 0;
	}

	private boolean isNameConstraintViolation(DataIntegrityViolationException exception) {
		Throwable cause = exception;
		while (cause != null) {
			if (cause instanceof ConstraintViolationException violation) {
				String constraintName = violation.getConstraintName();
				return NAME_UNIQUE_CONSTRAINT.equals(constraintName)
						|| constraintName != null && constraintName.endsWith("." + NAME_UNIQUE_CONSTRAINT);
			}
			cause = cause.getCause();
		}
		return false;
	}

	private ApiException validationError() {
		return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Invalid Seniority values");
	}

	private ApiException duplicateName() {
		return new ApiException(HttpStatus.CONFLICT, ErrorCode.SENIORITY_NAME_ALREADY_EXISTS,
				"Seniority name is already in use");
	}

	private ApiException rangeConflict() {
		return new ApiException(HttpStatus.CONFLICT, ErrorCode.SENIORITY_RANGE_CONFLICT,
				"Seniority range conflicts with an existing range");
	}

	private ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.SENIORITY_NOT_FOUND, "Seniority not found");
	}

	private ApiException inUse() {
		return new ApiException(HttpStatus.CONFLICT, ErrorCode.SENIORITY_IN_USE,
				"Seniority is currently in use by a Profile Skill");
	}

	private record CanonicalRange(String name, BigDecimal fromExperience, BigDecimal toExperience) {
	}
}
