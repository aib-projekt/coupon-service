# Technology Stack

## Overview
This document describes the technology choices and rationale for the **Coupon Service** — a production Java microservice.

## Languages

### Java (25)
- **Usage**: 100% of codebase
- **Rationale**: Enterprise-grade, strongly typed language well-suited for microservices; Java 25 provides modern language features and long-term support trajectory
- **Key Features Used**: Records, pattern matching, sealed classes (to be adopted as implementation progresses)

## Frameworks

### Frontend
N/A — this is a pure backend service with no frontend component.

### Backend
*Not yet declared.* Spring Boot is the recommended choice given the enterprise Java ecosystem and coupon/e-commerce domain requirements. To be finalized during initial implementation.

### Testing
*Not yet declared.* JUnit 5 + Mockito recommended for unit and integration testing.

## Database
*Not yet configured.* To be determined based on coupon domain requirements (relational database such as PostgreSQL is likely).

## Build Tools & Package Management

### Apache Maven
- **Version**: Managed via Maven Wrapper (`.mvn/`)
- **Rationale**: Industry-standard build tool for Java enterprise projects; wrapper ensures consistent builds across environments
- **Key Plugins**: To be configured (Compiler, Surefire, etc.)

## Infrastructure

### Containerization
Not yet configured. Docker is recommended for consistent deployment environments.

### CI/CD
Not yet configured.

### Hosting
Not yet determined.

## Development Tools

### IDE
IntelliJ IDEA — workspace pre-configured (`.idea/` present).

### Version Control
Git — `.gitignore` configured from project inception.

## Key Dependencies
Currently none declared. Initial dependencies to add:
- Spring Boot parent POM
- Database driver and connection pool
- JUnit 5 + Mockito (testing)
- SLF4J + Logback (logging)

## Version Management
Maven Wrapper (`.mvn/wrapper/`) pinned to ensure consistent Maven version across all developer machines and CI environments.

---
*Last Updated*: 2026-05-08
*Auto-detected*: Java version (pom.xml), Maven (pom.xml + .mvn/), IntelliJ IDEA (.idea/), Git (.gitignore)
*User-provided*: Project name (Coupon Service), purpose (production microservice)
