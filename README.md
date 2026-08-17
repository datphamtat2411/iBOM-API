# iBOM API

Backend API for the **iBOM CV/Profile Management System**.

## Tech Stack

- Java 17
- Spring Boot 3.x
- Maven
- MySQL 8
- Flyway
- Spring Data JPA
- Spring Security
- Bean Validation
- Swagger / OpenAPI
- JUnit 5
- Mockito
- MockMvc

## Local Database Setup

```sql
CREATE DATABASE ibom;
```

Configure the connection with environment variables. The defaults target a
local MySQL instance using the `root` user and an empty password; credentials
should be supplied through the environment rather than committed to source
control.

| Variable | Default |
| --- | --- |
| `DB_URL` | `jdbc:mysql://localhost:3306/ibom?serverTimezone=UTC` |
| `DB_USERNAME` | `ibom_user` |
| `DB_PASSWORD` ${DB_PASSWORD}

Hibernate runs in `validate` mode and Flyway is enabled.

## Verification

With MySQL running and the environment variables configured, run:

```bash
./mvnw clean compile
./mvnw test
./mvnw spring-boot:run
```

OpenAPI documentation is available at `/v3/api-docs` and Swagger UI at
`/swagger-ui.html`. The infrastructure health check is available at `/health`.

## Package Root

```text
com.fpt.ibom
```

