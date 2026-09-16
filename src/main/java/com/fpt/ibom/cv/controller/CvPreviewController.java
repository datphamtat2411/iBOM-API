package com.fpt.ibom.cv.controller;

import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.cv.service.CvPreviewService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cv")
public class CvPreviewController {

	private final CvPreviewService previewService;

	public CvPreviewController(CvPreviewService previewService) {
		this.previewService = previewService;
	}

	@GetMapping("/preview/{profileId}")
	public ResponseEntity<byte[]> preview(@AuthenticationPrincipal UserPrincipal principal,
			@PathVariable Long profileId) {
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
				.body(previewService.preview(principal, profileId));
	}
}
