package com.fpt.ibom.controller;

import java.util.Map;

import com.fpt.ibom.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class HealthController {

	@GetMapping
	public ApiResponse<Map<String, String>> health() {
		return new ApiResponse<>(200, "Success", Map.of("status", "UP"));
	}
}
