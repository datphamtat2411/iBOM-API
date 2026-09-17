package com.fpt.ibom.master.controller;

import com.fpt.ibom.common.ApiResponse;
import com.fpt.ibom.common.PageResponse;
import com.fpt.ibom.master.dto.FileNameFormatRequest;
import com.fpt.ibom.master.dto.FileNameFormatResponse;
import com.fpt.ibom.master.service.FileNameFormatService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/master/file-name-formats")
public class FileNameFormatController {

	private final FileNameFormatService fileNameFormatService;

	public FileNameFormatController(FileNameFormatService fileNameFormatService) {
		this.fileNameFormatService = fileNameFormatService;
	}

	@GetMapping
	public ResponseEntity<ApiResponse<PageResponse<FileNameFormatResponse>>> list(
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
		return ResponseEntity.ok(new ApiResponse<>(200, "Success", fileNameFormatService.list(page, size)));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<FileNameFormatResponse>> create(@Valid @RequestBody FileNameFormatRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(new ApiResponse<>(HttpStatus.CREATED.value(), "Success", fileNameFormatService.create(request)));
	}

	@PutMapping("/{fileNameFormatId}")
	public ResponseEntity<ApiResponse<FileNameFormatResponse>> update(@PathVariable Long fileNameFormatId,
			@Valid @RequestBody FileNameFormatRequest request) {
		return ResponseEntity.ok(new ApiResponse<>(200, "Success", fileNameFormatService.update(fileNameFormatId, request)));
	}

	@DeleteMapping("/{fileNameFormatId}")
	public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long fileNameFormatId) {
		fileNameFormatService.delete(fileNameFormatId);
		return ResponseEntity.ok(new ApiResponse<>(200, "Success", (Void) null));
	}
}
