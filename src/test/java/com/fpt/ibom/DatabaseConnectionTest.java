package com.fpt.ibom;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DatabaseConnectionTest {

	@Autowired
	private DataSource dataSource;

	@Test
	void connectsToConfiguredDatabase() throws SQLException {
		try (Connection connection = dataSource.getConnection()) {
			assertTrue(connection.isValid(2));
		}
	}
}
