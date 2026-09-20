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
| `DB_URL` | `jdbc:mysql://127.0.0.1:3306/ibom?serverTimezone=UTC` |
| `DB_USERNAME` |`${IBOM_USERNAME}`|
| `DB_PASSWORD` |`${IBOM_PASSWORD}`|

Hibernate runs in `validate` mode and Flyway is enabled.

## Initial Admin Bootstrap

Bootstrap provisioning is disabled by default. To create the single initial
admin account on startup, set these environment variables for that deployment:

| Variable | Purpose |
| --- | --- |
| `IBOM_AUTH_BOOTSTRAP_ENABLED` | Set to `true` to enable provisioning |
| `IBOM_AUTH_BOOTSTRAP_ADMIN_EMAIL` | Initial admin email |
| `IBOM_AUTH_BOOTSTRAP_ADMIN_USERNAME` | Initial admin username |
| `IBOM_AUTH_BOOTSTRAP_ADMIN_PASSWORD` | Initial admin password |

The credentials are validated and used only when no conflicting account exists.
The bootstrap password must satisfy the normal strong-password and allowed-domain
rules. Do not commit these values to source control.

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
