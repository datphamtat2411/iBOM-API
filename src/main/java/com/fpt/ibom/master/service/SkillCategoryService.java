package com.fpt.ibom.master.service;

import java.util.List;

import com.fpt.ibom.master.dto.SkillCategoryResponse;
import com.fpt.ibom.master.repository.SkillCategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkillCategoryService {

	private final SkillCategoryRepository skillCategoryRepository;

	public SkillCategoryService(SkillCategoryRepository skillCategoryRepository) {
		this.skillCategoryRepository = skillCategoryRepository;
	}

	@Transactional(readOnly = true)
	public List<SkillCategoryResponse> list() {
		return skillCategoryRepository.findAllByOrderByIdAsc().stream()
				.map(SkillCategoryResponse::from)
				.toList();
	}
}
