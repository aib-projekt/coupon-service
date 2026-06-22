## Development Conventions

### Predictable Structure
Organize files and directories in a logical, navigable layout.

### Up-to-Date Documentation
Keep README files current with setup steps, architecture overview, and contribution guidelines.

### Clean Version Control
Write clear commit messages, use feature branches, and add meaningful descriptions to pull requests.

### Environment Variables
Store configuration in environment variables; never commit secrets or API keys.

### Minimal Dependencies
Keep dependencies lean and up-to-date; document why major ones are included.

### Consistent Reviews
Follow a defined code review process with clear expectations for reviewers and authors.

### Testing Standards
Define required test coverage (unit, integration, etc.) before merging.

### Feature Flags
Use flags for incomplete features instead of long-lived branches.

### Changelog Updates
Maintain a changelog or release notes for significant changes.

### Build What's Needed
Avoid speculative code and "just in case" additions (see minimal-implementation.md).

### Technical Documentation in ./docs Folder
Place all technical documentation in the `./docs` folder. Only `README.md` should remain in the root directory.

**Examples**: `docs/API.md`, `docs/CONFIGURATION.md`, `docs/RUNBOOK.md`

### Each Module Must Have a README.md
Each module must have a `README.md` describing the module's purpose, configuration, and usage instructions.

### Use git mv for Java File Moves
When physically moving or renaming Java source files (e.g., during package reorganization), always use `git mv` instead of a filesystem move followed by `git add`. `git mv` preserves git blame and history on the moved file; a copy+delete loses the full commit history.

**Preferred**: `git mv src/main/java/com/hrs/.../OldPackage/Foo.java src/main/java/com/hrs/.../NewPackage/Foo.java`
**Avoid**: File manager or IDE move that results in `deleted` + `untracked` in git status

### Isolate Behavioral Changes in Separate Commits During Refactors
When a structural refactor (e.g., package reorganization) includes both mechanical renames and genuine behavioral changes, commit them separately. Mechanical renames belong in one commit; behavioral changes (e.g., AOP pointcut expression rewrites, Spring configuration changes) belong in a second commit. This makes the behavioral change auditable in isolation and reduces revert scope if an issue is discovered.
