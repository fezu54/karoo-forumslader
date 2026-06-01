# Agents guidelines for karoo-forumslader

Purpose
- Instructions for GitHub Copilot / Gemini agents to generate, modify, and review code in this repository.
- Keep suggestions short, precise, and idiomatic Kotlin.

High-level rules
- Prefer Kotlin scope functions (let, run, apply, also, with) to reduce nested ifs where appropriate.
- Prefer `when` over nested `if-else if` chains.
- Use named parameters for public functions and constructors where it improves clarity. Don't use it if it parameter and variable names are the same.
- Use clear, self-explaining names for classes, functions and variables; avoid comments when names suffice.
- Follow Kotlin coding conventions (package and file names, camelCase for functions/variables, PascalCase for types).
- Keep code short and focused: small functions and single-responsibility classes.
- Prefer onion architecture (domain → application → adapters → frameworks) over layered architecture.
- Always add tests for production code. Tests must run on CI and be included in the same PR as the implementation.

Architecture expectations
- Domain: pure Kotlin, no framework dependencies. Define entities, value objects, and domain services here.
- Application: use cases / interactors, orchestrating domain calls. Expose simple interfaces.
- Adapters: implementations of repository, gateway, and input/output adapters. Keep framework code here.
- Frameworks: DI, web, database drivers. Keep wiring and external concerns here.
- Dependency rule: inner rings must not depend on outer rings. Use interfaces at boundaries.

Kotlin style specifics
- Prefer scope functions to avoid repeated null checks and to keep transformations local:
  - Use `?.let { ... }` for null-safe chains.
  - Use `run` for executing blocks that return a value and need local scope.
  - Use `apply` and `also` for configuring objects.
- Use `when` to handle multiple branches instead of nested `if` blocks.
- Favor expression-bodied functions for short implementations.
- Use named parameters in calls when it clarifies intent, especially for functions with multiple parameters of the same type. Avoid it when it just mirrors the name.
- Keep functions small (ideally under ~40 lines) and with a single reason to change.

Naming and self-documenting code
- Use descriptive names: calculateInvoiceTotal, userRepository, validateUserInput.
- Avoid comments that explain what the code does; instead rename or extract to a well-named function.
- Use Kotlin data classes for simple DTOs and value objects.

Testing
- Every production change must include tests. Unit tests first, then integration tests if necessary.
- Use testing frameworks (e.g., JUnit, MockK) with versions maintained in `gradle/libs.versions.toml`.
- Tests must be deterministic and not rely on external services; use fakes or in-memory adapters for CI.
- Add clear test names in full sentences with whitespaces using back ticks which describes the wanted behavior following the pattern: `should do something when action executed`.
- Define `//given`, `//when`, `//then` comments if it adds clarity

Commit & PR rules
- Follow Conventional Commits for commit messages. Examples:
  - feat(scope): add user registration use case
  - fix(auth): handle token expiry in refresh flow
  - test(repo): add unit tests for UserRepositoryAdapter
  - docs: add agents.md
- Keep commits small and focused. One logical change per commit.
- Each PR must include:
  - a short description of intent
  - tests demonstrating the change
  - a changelog entry if the change affects public behavior

Agent-specific behavior
- When proposing code, include minimal necessary code; prefer creating small focused commits/PRs.
- Always include or modify tests in the same suggestion that adds production logic.
- Do not add TODO comments as a substitute for tasks; instead create issues for significant follow-ups.
- If a suggested change affects architecture or introduces new dependencies, explain briefly which ring it belongs to and why.

Examples
- Prefer `when` over nested ifs:
  ```kotlin
  fun statusMessage(status: Status) = when (status) {
      Status.OK -> "ok"
      Status.ERROR -> "error"
      Status.UNKNOWN -> "unknown"
  }
  ```

- Use scope functions to reduce nesting:
  ```kotlin
  fun parseAndHandle(input: String?) {
      input?.let { cleaned ->
          val parsed = parse(cleaned)
          parsed.handle()
      }
  }
  ```

- Use named parameters:
  ```kotlin
  createUser(name = "Alice", email = "a@acme.com", isAdmin = false)
  ```

Enforcement
- CI will run formatting, linting, and tests. Fix failures before merging.
- Reviewers should reject changes that:
  - introduce nested if chains instead of `when` or scope functions when a clearer alternative exists
  - lack tests for production behavior
  - violate onion dependency rules

Contact
- If unsure, open an issue describing the ambiguity and propose two concise implementation options.
