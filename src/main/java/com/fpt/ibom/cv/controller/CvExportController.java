package com.fpt.ibom.cv.controller;

import com.fpt.ibom.auth.security.UserPrincipal;
import com.fpt.ibom.cv.model.CvExportResult;
import com.fpt.ibom.cv.service.CvExportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cv")
public class CvExportController {

	private static final MediaType DOCX_MEDIA_TYPE = MediaType.parseMediaType(
			"application/vnd.openxmlformats-officedocument.wordprocessingml.document");

	private final CvExportService exportService;

	public CvExportController(CvExportService exportService) {
		this.exportService = exportService;
	}

	@GetMapping("/download/{profileId}")
	public ResponseEntity<byte[]> download(@AuthenticationPrincipal UserPrincipal principal,
			@PathVariable Long profileId, @RequestParam String format,
			@RequestParam(required = false) Long fileNameFormatId) {
		CvExportResult result = exportService.export(principal, profileId, format, fileNameFormatId);
		MediaType mediaType = "pdf".equals(result.format()) ? MediaType.APPLICATION_PDF : DOCX_MEDIA_TYPE;
		return ResponseEntity.ok().contentType(mediaType)
				.header("Content-Disposition", ContentDisposition.attachment().filename(result.fileName()).build().toString())
				.body(result.bytes());
	}
}
