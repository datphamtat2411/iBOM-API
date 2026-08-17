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
| `DB_USERNAME` | `root` |
| `DB_PASSWORD` | empty |

Hibernate runs in `validate` mode and Flyway is enabled. Flyway owns schema
evolution; no business migration is included until a task defines the first
business schema.

## Verification

With MySQL running and the environment variables configured, run:

```bash
./mvnw clean compile
./mvnw test
./mvnw spring-boot:run
```


## Package Root

```text
com.fpt.ibom
