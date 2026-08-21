package com.fpt.ibom;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;

@ActiveProfiles("test")
@Import(MySqlIntegrationTest.ContainerConfiguration.class)
public abstract class MySqlIntegrationTest {

	@TestConfiguration(proxyBeanMethods = false)
	static class ContainerConfiguration {

		@Bean
		@ServiceConnection
		MySQLContainer<?> mysqlContainer() {
			return new MySQLContainer<>("mysql:8.0.46")
					.withDatabaseName("ibom_test")
					.withUsername("ibom_test")
					.withPassword("ibom_test_password");
		}
	}
}
