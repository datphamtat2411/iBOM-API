package com.fpt.ibom.config;

import java.util.List;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	@Bean
	OpenAPI ibomOpenApi() {
		return new OpenAPI()
				.info(new Info().title("iBOM API").version("v1").description("iBOM API documentation"))
				.servers(List.of(new Server().url("/")));
	}
}
