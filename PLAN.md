# API Foundation Plan

## Objective

Establish a robust HTTP/API foundation for the iBOM API that supports future business controllers while maintaining consistency with existing patterns and documentation.

## Current repository observations

The repository is a Spring Boot 3.5.16 application with:
- Maven build configuration
- Spring Web, JPA, MySQL, and Flyway dependencies
- Basic application structure with IbomApiApplication class
- Existing documentation covering architecture, API conventions, and testing
- Application properties configured for MySQL database connection
- Initial test suite with context loading and database connection tests

## Technical decisions and rationale

### Core Framework
- Leverage existing `spring-boot-starter-web` dependency for HTTP handling
- Utilize Spring MVC as the underlying web framework
- Adopt Spring Boot's autoconfiguration for standard web components

### API Response Model
- Implement a consistent response wrapper with `code`, `message`, `data`, and `timestamp`
- Follow documented API conventions from API.md
- Support both success and error responses with uniform structure
- Avoid unnecessary abstraction layers or builders

### Exception Handling
- Implement centralized `@ControllerAdvice` for consistent error response formatting
- Map standard HTTP status codes to appropriate response codes
- Handle validation errors specifically to return field-level validation information
- Support common HTTP status semantics: 400, 401, 403, 404, 409, 500

### Bean Validation
- Integrate `spring-boot-starter-validation` for request DTO validation
- Maintain separation between request validation (API boundary) and business validation (service layer)
- Plan validation error responses that follow the common API contract

### OpenAPI/Swagger
- Use Springdoc OpenAPI (recommended for Spring Boot 3.x)
- Configure minimal setup for automatic API documentation generation
- Ensure compatibility with current Spring Boot version (3.5.16)

## Dependencies to add/change

### New Dependencies
1. `spring-boot-starter-validation` - for Bean Validation support
2. `springdoc-openapi-starter-webmvc-ui` - for OpenAPI/Swagger support

## Files to add

### Response Model
- `src/main/java/com/fpt/ibom/common/ApiResponse.java` - Generic API response wrapper

### Exception Handling
- `src/main/java/com/fpt/ibom/exception/GlobalExceptionHandler.java` - Centralized exception handler
- `src/main/java/com/fpt/ibom/exception/ApiException.java` - Base API exception class

### Validation Support
- `src/main/java/com/fpt/ibom/validation/ValidationErrorResponse.java` - Structure for validation error responses

### Configuration
- `src/main/java/com/fpt/ibom/config/OpenApiConfig.java` - OpenAPI configuration class

### Test Infrastructure
- `src/test/java/com/fpt/ibom/controller/AbstractControllerTest.java` - Base test class for controllers
- `src/test/java/com/fpt/ibom/controller/HealthControllerTest.java` - Example test for API foundation

## Files to modify

### pom.xml
- Add `spring-boot-starter-validation` dependency
- Add `springdoc-openapi-starter-webmvc-ui` dependency

## Files to delete

None

## Implementation sequence

1. Add necessary dependencies to pom.xml
2. Create API response model
3. Implement global exception handler
4. Create validation error structure
5. Set up OpenAPI configuration
6. Add test infrastructure
7. Verify application startup and API behavior

## API response and error-handling behavior

### Success Responses
- Use `ApiResponse<T>` wrapper with `code: 200`, `message: "Success"`, and populated `data`
- Return appropriate HTTP 200 status

### Error Responses
- Use `ApiResponse<Object>` wrapper with appropriate `code` based on HTTP status
- Include `message` with descriptive error text
- For validation errors, include `data` with field-level validation information
- Timestamp automatically populated

### HTTP Status Mapping
- 400 Bad Request → `code: 400`
- 401 Unauthorized → `code: 401`  
- 403 Forbidden → `code: 403`
- 404 Not Found → `code: 404`
- 409 Conflict → `code: 409`
- 500 Internal Server Error → `code: 500`

## Validation behavior

### Request Validation
- Use Bean Validation annotations on request DTOs
- Validation occurs at API boundary (controller layer)
- Validation errors return HTTP 400 with structured error response
- Field names and constraint violations mapped to validation error response

### Business Validation
- Business/service-layer validation handled separately
- Maintains distinction between request shape validation and business logic validation
- Future service layer can leverage business-specific validation without affecting API boundary

## OpenAPI/Swagger setup

### Configuration
- Use Springdoc OpenAPI for automatic API documentation
- Configure base path and info for OpenAPI specification
- Enable Swagger UI by default
- Generate OpenAPI JSON/YAML at `/v3/api-docs` and UI at `/swagger-ui.html`

### Documentation
- No business endpoints created yet
- Focus on infrastructure foundation only
- Ensure OpenAPI spec reflects the API contract structure

## Automated tests

### Test Coverage
- Successful API response serialization
- Request validation failure returning 400 with proper error structure
- Validation errors following common response contract
- Handled application exception mapping to intended HTTP status and response structure
- Unexpected server error handling where practical

### Testing Strategy
- Use MockMvc for controller-level testing
- Follow existing repository test patterns from TESTING.md
- Test both success and error scenarios
- Verify response structure and HTTP status codes
- Test exception handling edge cases

## Command-line/manual verification

### Build Verification
- Run `./mvnw clean compile` to verify compilation
- Run `./mvnw test` to validate all tests pass
- Run `./mvnw spring-boot:run` to confirm application startup

### API Verification
- Access `/v3/api-docs` to verify OpenAPI specification availability
- Access `/swagger-ui.html` to verify Swagger UI availability
- Confirm previous MySQL/JPA/Flyway foundation still starts correctly
- Validate health check endpoint (to be added) works

## Documentation changes required during BUILD, if any

- Update README.md to reflect new API foundation components
- Possibly update API.md to document the new exception handling behavior
- No new documentation files required beyond what's already planned

## Additional repository docs BUILD should read

- ARCHITECTURE.md for layer boundaries understanding
- CONVENTIONS.md for coding standards and patterns
- TESTING.md for test structure expectations

## Explicit out-of-scope items

- User/Auth logic
- Login functionality
- JWT implementation
- Spring Security configuration
- Role or ownership rules
- Business controllers
- Business services
- Repositories or entities
- Database migrations
- Profile functionality
- Pagination frameworks
- Speculative generic API frameworks
- Future business-specific exception classes
- CI/CD changes
- Unrelated refactoring

## Unresolved/TBD items

- Specific OpenAPI configuration parameters (title, version, description)
- Whether to enable/disable specific Swagger features
- Exact structure of validation error responses (field names vs. paths)

## Definition of Done

- [ ] All required dependencies added to pom.xml
- [ ] API response model implemented
- [ ] Global exception handler configured
- [ ] Validation error structure implemented
- [ ] OpenAPI/Swagger configured
- [ ] Test infrastructure in place
- [ ] Maven builds successfully
- [ ] Application starts without errors
- [ ] OpenAPI specification available
- [ ] Swagger UI accessible
- [ ] Basic test coverage for API foundation behaviors