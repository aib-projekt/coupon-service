# Documentation Index

**IMPORTANT**: Read this file at the beginning of any development task to understand available documentation and standards.

## Quick Reference

### Project Documentation
Project-level documentation covering technology choices and system architecture for the Coupon Service microservice.

### Technical Standards
Coding standards, conventions, and best practices organized by domain. Initialized for: global, backend, testing.

---

## Project Documentation

Located in `.maister/docs/project/`

#### Technology Stack (`project/tech-stack.md`)
Java 25 backend microservice using Apache Maven (via Maven Wrapper) for builds, IntelliJ IDEA as IDE, Git for version control. Spring Boot recommended for the backend framework; JUnit 5 + Mockito for testing; PostgreSQL likely for persistence. All framework/database choices are pending finalization during initial implementation.

#### System Architecture (`project/architecture.md`)
Layered REST microservice pattern with four tiers: API layer (HTTP controllers), Application/Service layer (business rules, coupon validation and redemption flows), Domain layer (core entities and value objects), and Infrastructure/Persistence layer (repository interfaces). Data flow: HTTP request → API → Service → Domain → Persistence → Response. Project is currently in pre-implementation skeleton phase; database schema, external integrations, and deployment architecture are pending configuration.

---

## Technical Standards

### Global Standards

Located in `.maister/docs/standards/global/`

#### Coding Style (`standards/global/coding-style.md`)
Naming consistency across variables/functions/classes/files, automated formatting enforcement, descriptive identifier names, and avoidance of cryptic abbreviations.

#### Commenting (`standards/global/commenting.md`)
Self-documenting code over comments, sparse commenting only for non-obvious logic, no change-log style comments — comments must be timeless explanations.

#### Development Conventions (`standards/global/conventions.md`)
Predictable file/directory structure, up-to-date README maintenance, clean version control (clear commit messages, feature branches, meaningful PR descriptions), minimal dependencies, and consistent language/tooling.

#### Error Handling (`standards/global/error-handling.md`)
Clear user-facing messages without internal detail leakage, fail-fast input validation, typed/specific exception classes, centralized error handling, and consistent HTTP error response formats.

#### Java Conventions (`standards/global/java-conventions.md`)
Java 21 LTS requirement enforced by maven-enforcer-plugin, Jakarta EE (jakarta.*) packages over javax, English-only code and documentation, UTF-8 source encoding.

#### Minimal Implementation (`standards/global/minimal-implementation.md`)
Build only what is called, every method must serve a purpose, delete exploration artifacts, avoid speculative abstractions and premature generalization.

#### Validation (`standards/global/validation.md`)
Server-side validation always required, client-side for UX feedback only, validate early and reject invalid data before processing, use constraint annotations, return structured validation error responses.

---

### Backend Standards

Located in `.maister/docs/standards/backend/`

#### API Design (`standards/backend/api.md`)
RESTful resource naming, HTTP method semantics, consistent response envelope structure, proper status code usage, and versioning conventions.

#### Audit Patterns (`standards/backend/audit-patterns.md`)
Audit entity structure, AuditStorage service patterns, repository conventions for audit data, event-sourcing patterns, and audit trail consistency rules.

#### AWS Standards (`standards/backend/aws.md`)
AWS service usage conventions, configuration management via environment variables, and cloud-resource naming patterns.

#### Build Standards (`standards/backend/build.md`)
Maven build configuration, plugin management, enforcer rules (banned dependencies, Java version), build lifecycle conventions, and CI integration.

#### Docker Standards (`standards/backend/docker.md`)
Base image selection (eclipse-temurin:21-jre), Dockerfile structure, image layering, and container configuration conventions.

#### Local Development Standards (`standards/backend/local-dev.md`)
Local environment setup, Docker Compose usage, environment variable configuration, database seeding, and developer workflow conventions.

#### Logging Standards (`standards/backend/logging.md`)
Log level usage (DEBUG/INFO/WARN/ERROR), structured logging fields, avoiding sensitive data in logs, correlation ID propagation, and log format conventions.

#### Lombok Standards (`standards/backend/lombok.md`)
Approved Lombok annotations, annotations to avoid, integration with Jakarta validation, and builder/constructor patterns.

#### Database Migrations (`standards/backend/migrations.md`)
Migration file naming, incremental schema changes, rollback strategy, and Flyway/Liquibase conventions.

#### Models (`standards/backend/models.md`)
Entity class structure, field naming, relation mapping conventions, audit fields, and DTO/entity separation rules.

#### Naming Conventions (`standards/backend/naming-conventions.md`)
Package, class, method, variable, constant, and database object naming patterns for Java/Spring Boot projects.

#### Database Queries (`standards/backend/queries.md`)
Repository method naming, JPQL/native query conventions, N+1 prevention, pagination patterns, and query parameter binding.

#### Spring Boot Standards (`standards/backend/spring-boot.md`)
Application configuration structure, bean declaration conventions, profile usage, dependency injection style, and Spring component organization.

---

### Testing Standards

Located in `.maister/docs/standards/testing/`

#### JaCoCo Coverage Standards (`standards/testing/jacoco.md`)
80% minimum instruction and branch coverage gate (haltOnFailure), jacoco-maven-plugin configuration, GitLab CI integration via JACOCO_MINIMUM_COVERAGE env var, and coverage exclusion rules.

#### JUnit Patterns (`standards/testing/junit-patterns.md`)
Given-When-Then comment structure in all test methods, test class/method naming, use of @ExtendWith(MockitoExtension.class), Mockito stubbing conventions, and assertion style.

#### Test Writing (`standards/testing/test-writing.md`)
Test behavior over implementation, one assertion per concept, test isolation, avoid testing private methods, and meaningful test naming that describes the scenario.

---

### Frontend Standards

*Not initialized for this project. If you need frontend standards, you can:*
- *Add them manually using the docs-manager skill*
- *Run `/maister:standards-discover --scope=frontend` to auto-discover*

---

## How to Use This Documentation

1. **Start Here**: Always read this INDEX.md first to understand what documentation exists
2. **Project Context**: Read relevant project documentation before starting work
3. **Standards**: Reference appropriate standards when writing code
4. **Keep Updated**: Update documentation when making significant changes
5. **Customize**: Adapt all documentation to your project's specific needs

## Updating Documentation

- Project documentation should be updated when goals, tech stack, or architecture changes
- Technical standards should be updated when team conventions evolve
- Always update INDEX.md when adding, removing, or significantly changing documentation
