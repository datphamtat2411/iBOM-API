# FND-01 — Runtime & Persistence Foundation

## Task Objective

Establish the minimum backend runtime and persistence foundation for the iBOM API, enabling future business features to safely build upon a solid infrastructure. This involves setting up Maven dependencies, runtime configuration, MySQL 8 connectivity, Spring Data JPA infrastructure, and Flyway schema management.

## Current Repository Observations

The repository currently has a basic Spring Boot 3.x structure with:
- Java 17 as the development version
- Spring Boot 3.5.16 parent dependency
- Minimal dependencies (only spring-boot-starter and spring-boot-starter-test)
- Basic application structure with IbomApiApplication class
- Empty application.properties file
- Standard Spring Boot testing configuration

No database dependencies, configuration, or persistence infrastructure have been implemented yet.

## Technical Decisions and Rationale

### 1. Maven Dependencies

**Required Dependencies:**
- `spring-boot-starter-data-jpa` - For Spring Data JPA infrastructure
- `mysql-connector-java` - MySQL 8 JDBC driver
- `flyway-core` - Flyway database migration support (included via Spring Boot starter)

**Deferred Dependencies (Not part of FND-01):**
- `spring-boot-starter-web` - REST API layer (future task)
- `spring-boot-starter-validation` - Bean validation (future task)
- `spring-boot-starter-security` - Security (future task)
- `springdoc-openapi-starter-webmvc-ui` - Swagger/OpenAPI (future task)
- `jjwt` - JWT libraries (future task)
- `lombok` - Utility annotations (future task)

The decision to defer these dependencies is based on the explicit constraint that FND-01 should only include foundational elements necessary for runtime and persistence, without leaking future feature infrastructure.

### 2. Runtime Configuration

**Configuration Strategy:**
- Leverage Spring Boot's externalized configuration with `application.properties`
- Use environment variables for sensitive data (database credentials)
- Provide sensible non-secret defaults for development
- Document required configuration properties in README

**Expected Configuration Properties:**
- `spring.datasource.url` - Database connection URL
- `spring.datasource.username` - Database username 
- `spring.datasource.password` - Database password
- `spring.jpa.hibernate.ddl-auto` - Hibernate schema management (set to validate to prevent auto-creation)
- `spring.flyway.enabled` - Enable/disable Flyway (should be enabled)

### 3. MySQL Provisioning Boundary

**Strategy:** External local prerequisite
- MySQL 8 must be installed and running locally
- No Docker/Compose files introduced in FND-01
- Documentation will specify required setup steps
- Developers should configure MySQL manually or use their preferred local DB solution

This approach aligns with the requirement that FND-01 should not introduce Docker/Compose merely because it's common practice, but rather only when there's a concrete need.

### 4. JPA Schema Management

**Configuration:**
- Set `spring.jpa.hibernate.ddl-auto=validate` to prevent Hibernate from creating/updating schema
- Configure proper JPA/Hibernate settings for MySQL 8 compatibility
- Ensure Flyway owns schema evolution exclusively
- Use Spring Data JPA repositories for persistence operations

This follows the constraint that Hibernate should not silently create/update the production schema, and Flyway is the authoritative schema-migration mechanism.

### 5. Flyway Baseline

**Approach:**
- Configure Flyway to manage schema evolution
- Do not create meaningless V1 migration in this task
- Document that first business migrations will be created in subsequent tasks
- Ensure Flyway integration is verified through build/test process

Since FND-01 intentionally has no business entities yet, we won't create any meaningful migrations. The Flyway configuration will be set up to enable schema management, but the first actual migrations will be part of later business tasks.

### 6. Test and Verification Strategy

**Automated Tests:**
- Maintain existing `IbomApiApplicationTests` which validates Spring context loading
- Add minimal database connectivity test to verify datasource configuration
- Ensure existing tests continue to pass with new configuration

**Command-line Verification:**
- Maven build (`./mvnw clean compile`) - to verify dependencies resolve
- Maven test (`./mvnw test`) - to verify Spring context loads and basic functionality
- Manual verification of application startup with proper database config
- Flyway validation that it's properly configured

## Files to Add

1. **`src/main/resources/application.properties`** - Enhanced with database configuration
2. **`src/test/java/com/fpt/ibom/DatabaseConnectionTest.java`** - Basic database connectivity test

## Files to Modify

1. **`pom.xml`** - Add required Maven dependencies:
   - `spring-boot-starter-data-jpa`
   - `mysql-connector-java`

## Files to Delete

None

## Implementation Sequence

1. Update `pom.xml` with required dependencies
2. Enhance `application.properties` with database configuration
3. Create basic database connectivity test
4. Verify Maven build and test execution
5. Document configuration requirements in README

## Runtime/Environment Configuration

The application will use Spring Boot's externalized configuration approach:
- Database credentials will be loaded from environment variables
- Default configuration will be provided in `application.properties`
- Environment-specific profiles can be added later if needed
- Sensitive information like passwords should never be committed to source control

## JPA/Flyway Behavior

- Hibernate schema management will be set to `validate` mode to prevent automatic schema creation
- Flyway will be enabled and configured to manage all schema changes
- All database schema evolution will be handled by Flyway migrations
- JPA entities will be configured for MySQL 8 compatibility
- Proper transaction management will be enabled for persistence operations

## Automated Test Impact

- Existing `IbomApiApplicationTests` will continue to validate Spring context loading
- New `DatabaseConnectionTest` will verify database connectivity configuration
- All tests will continue to run with the enhanced configuration
- No breaking changes to existing test structure or behavior

## Command-line Verification Steps

1. Run `./mvnw clean compile` to verify dependencies resolve correctly
2. Run `./mvnw test` to verify Spring context loads and basic functionality
3. Manually start application with proper database configuration to verify connectivity
4. Check that Flyway migrations are properly configured and would run if migrations existed
5. Verify that Hibernate operates in `validate` mode and doesn't attempt schema creation

## Documentation Changes Required During BUILD

1. Update `README.md` to document:
   - Required MySQL 8 installation
   - Database configuration properties
   - Environment variable usage
   - Verification steps

## Documentation BUILD Should Read

1. `docs/ARCHITECTURE.md` - For understanding the overall system structure
2. `docs/DATA.md` - For understanding the eventual database schema requirements
3. `docs/CONVENTIONS.md` - For maintaining consistency with existing patterns
4. `docs/TESTING.md` - For understanding testing expectations

## Explicit Out-of-Scope Items

- User/Auth entities or repositories
- `users`, `refresh_tokens`, or `verification_codes` schema
- Profile or other business schema
- Controllers or business APIs
- Common `ApiResponse`
- Global exception handling
- Swagger/OpenAPI
- Spring Security configuration
- Login/JWT/refresh token behavior
- SMTP
- Business services
- Speculative base entities
- Generic repository/service abstractions
- Full package skeletons with empty classes
- CI/CD workflows
- Final JaCoCo enforcement
- Future performance/index optimization

## Unresolved/TBD Items

1. Whether to use `spring-boot-starter-jdbc` instead of `spring-boot-starter-data-jpa`
2. Specific Hibernate dialect settings for MySQL 8
3. Exact Flyway configuration parameters

## Definition of Done for FND-01

1. Maven dependencies for JPA, MySQL, and Flyway are properly configured
2. Application can be built successfully with all dependencies resolved
3. Runtime configuration supports environment-based database credentials
4. Database connectivity is validated through automated test
5. Hibernate is configured in validate mode (no automatic schema creation)
6. Flyway is properly integrated and ready for future migrations
7. Existing tests continue to pass
8. README documents the required configuration and setup steps
9. No business feature or future infrastructure leaks into the foundation
10. Application can start and connect to MySQL 8 with proper configuration