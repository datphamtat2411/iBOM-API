package com.fpt.ibom.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = HealthController.class)
@ActiveProfiles("test")
abstract class AbstractControllerTest {

	@Autowired
	protected MockMvc mockMvc;
}
