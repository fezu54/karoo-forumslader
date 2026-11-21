# Coding Standards for karoo-forumslader

Standards enforced during code review. Keep changes minimal, idiomatic Kotlin, and covered by deterministic tests.

## Architecture (Onion Rings)

Organize code into four concentric rings (`domain` → `application` → `adapters` → `frameworks`):

- **Domain**: Pure Kotlin with zero Android or framework dependencies (entities, value objects, domain services).
- **Application**: Use cases and interactors orchestrating domain calls behind simple interfaces.
- **Adapters**: Repository, gateway, BLE, and Karoo SDK implementations.
- **Frameworks**: DI, Room persistence, and wiring.

Inner rings must not depend on outer rings; invert dependencies with interfaces at boundaries. When a change affects architecture or adds a dependency, state which ring it belongs to and why.

## Kotlin Style

- Prefer `when` over nested `if-else if` chains and scope functions (`?.let`, `run`, `apply`, `also`) to keep null checks and transformations flat.
- Use named parameters where they clarify intent (especially for adjacent parameters of the same type); omit them when the argument variable already matches the parameter name.
- Keep functions small (ideally under ~40 lines) with a single reason to change; favor expression-bodied functions for short implementations.
- Prefer small, single-purpose objects and data classes (OOP + YAGNI) over procedural helpers or speculative abstractions.
- Use Kotlin's multi-dollar string interpolation prefix (e.g. `$$"$FLT,6"`) instead of backslash-escaping `$`.
- Import all types at the top of the file; avoid inline fully qualified names (e.g. `java.util.UUID`).
- Prefer renaming or extracting a function over explanatory comments; track follow-up work in issues instead of `TODO` comments.

## Testing

- Include or update tests in the same change as production logic (unit tests first, integration/Robolectric tests where needed).
- Keep tests deterministic and offline using fakes or in-memory adapters; declare test dependencies in `gradle/libs.versions.toml`.
- Name tests as full-sentence behavior descriptions (e.g. `` `should do something when action executed` ``) and use `// given`, `// when`, `// then` comments where they clarify flow.
- Keep line coverage at or above `.github/coverage-baseline.txt` (`./gradlew :app:checkCoverageBaseline`).

## Commits & PRs

- Use Conventional Commits (`feat(scope): ...`, `fix(scope): ...`, `test(scope): ...`, `docs: ...`; enforced by `.githooks/commit-msg`).
- Always include a descriptive commit body (separated from the subject by a blank line) explaining *why* the change is needed and *what* was changed — never commit with only a single-line subject.
- Keep each commit focused on one logical change.
- Include a short description of intent, tests demonstrating the change, and updated docs when public behavior changes.
