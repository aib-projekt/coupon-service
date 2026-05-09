## Java Conventions

### Java 25 Required
All application code must use Java 25. Enforced by maven-enforcer-plugin and eclipse-temurin:25-jre Docker base image.

### Jakarta EE Over javax
Use Jakarta EE (jakarta.*) packages. javax.persistence and javax.transaction are banned via maven-enforcer bannedDependencies.

**Preferred**: `import jakarta.validation.constraints.NotNull`
**Avoid**: `import javax.validation.constraints.NotNull`

### Use English for Code and Documentation
All code, comments, variable names, method names, and documentation must be written in English.

### UTF-8 Source Encoding
All source files use UTF-8 encoding. Enforced at Maven compiler level via `<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>`.
