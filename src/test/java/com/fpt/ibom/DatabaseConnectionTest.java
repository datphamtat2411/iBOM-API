package com.fpt.ibom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.MySQLContainer;

@SpringBootTest
class DatabaseConnectionTest extends MySqlIntegrationTest {

	@Autowired
	private DataSource dataSource;
	@Autowired
	private MySQLContainer<?> mysqlContainer;

	@Test
	void connectsToTestcontainerDatabase() throws SQLException {
		try (Connection connection = dataSource.getConnection()) {
			assertTrue(mysqlContainer.isRunning());
			assertTrue(connection.isValid(2));
			assertEquals(mysqlContainer.getJdbcUrl(), connection.getMetaData().getURL());
			assertEquals(mysqlContainer.getDatabaseName(), connection.getCatalog());
		}
	}
}
