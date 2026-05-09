## API Design

### RESTful Principles
Use resource-based URLs with appropriate HTTP methods (GET, POST, PUT, PATCH, DELETE).

### Consistent Naming
Use lowercase, hyphenated or underscored names consistently across endpoints.

### Versioning
Implement versioning (URL path or headers) to manage breaking changes.

### Plural Nouns
Use plural nouns for resources (`/users`, `/products`).

### Limited Nesting
Keep URL nesting to 2-3 levels maximum for readability.

### Query Parameters
Use query parameters for filtering, sorting, and pagination.

### Proper Status Codes
Return appropriate HTTP status codes (200, 201, 400, 404, 500).

### Rate Limit Headers
Include rate limit information in response headers.

### REST Base Path Convention
Use `/api/{domain}/{resource}` as the base path pattern. Domain groups related services; resource uses plural nouns.

**Example**: `/api/audit/events`

### ResponseEntity with Typed DTO Bodies
All REST endpoints return `ResponseEntity<T>` with typed DTO bodies:
- `POST` operations → `ResponseEntity<AuditResponse>`
- `GET` collections → `ResponseEntity<List<T>>`
- Errors → `ResponseEntity<ErrorResponse>`

### @ConditionalOnProperty for Feature Toggles
Use `@ConditionalOnProperty` to conditionally activate optional features. This allows running the service without optional integrations (e.g., SQS).

```java
@ConditionalOnProperty(name = "aws.sqs.enabled", havingValue = "true")
```
