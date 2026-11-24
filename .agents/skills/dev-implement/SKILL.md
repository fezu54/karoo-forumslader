---
name: dev-implement
description: >-
  Executes test-driven, token-efficient implementation in an isolated git worktree
  following karoo-forumslader onion architecture, Kotlin rules, and German
  translation parity. Use after dev-issue-gate clears an issue or small bug fix.
---

# Stage 2: TDD & Token-Saving Implementation

Proceed only after [dev-issue-gate](../dev-issue-gate/SKILL.md) confirms requirements are clear and any required issue exists.

## 1. Isolate Workspace (Worktree Default)

- Check current checkout first: if `.git` is a regular file (`[ -f .git ]`), you are already inside a linked worktree — stay in it and ensure you are on a non-`main` branch.
- Otherwise, **by default** create a fresh worktree under `.worktrees/` using the Kotlin helper:
  ```bash
  .agents/skills/dev-implement/scripts/setup-worktree.sh <branch-name> [base-ref]
  ```
  Examples: `feat/123-short-slug`, `fix/short-slug`, `refactor/scope-slug`. The helper symlinks gitignored `local.properties` and verifies `.githooks`.
- Run all edits, Gradle tests, and git commands inside the created `.worktrees/<slug>` directory.
- Work directly on a git branch in the main checkout **only** if the user explicitly asks to use a branch instead of a worktree.

## 2. Token-Saving Rules

1. **No pre-edit Gradle runs**: Never run `./gradlew build`, `assemble*`, `test*`, `check*`, `lint*`, or `kover*` before modifying code.
2. **Never unpack `.aar` or `.jar` files**: Do not run `unzip`, `jar`, `javap`, `zipinfo`, or scripts against `.gradle/caches`, `karoo-ext`, or `kable`. Read [references/karoo-ext-api.md](./references/karoo-ext-api.md) and existing usages in `app/src/main/kotlin/.../extension/`.
3. **Read economically**: Search with `rg -n`, read targeted line ranges, batch parallel reads, and load domain skills (`battery-range-estimation`, `forumslader-protocol`) only when touching those areas.
4. **Targeted Gradle iteration**: During TDD Red/Green cycles, run only the affected test class:
   ```bash
   ./gradlew :app:testDebugUnitTest --tests "org.happycode.karoo.forumslader.<pkg>.<TestClass>"
   ```
5. **Single final verification**: Run `./gradlew :app:checkCoverageBaseline` **once** after all code edits are complete (it runs `koverXmlReport` -> `testDebugUnitTest` and checks `.github/coverage-baseline.txt`). On failure, inspect only error lines — never dump full logs.

## 3. Test-Driven Development (TDD) with Human Review Gate

1. **Red — Write unit tests first**:
   - Cover acceptance criteria, edge cases, and invariants with deterministic unit tests using sentence backtick names (`` `should ... when ...` ``) and `// given` / `// when` / `// then` comments.
   - If new types or signatures are needed for compilation clarity, add minimal unimplemented stubs only (`TODO()` or `throw UnsupportedOperationException(...)`) — **do not implement production logic or run Gradle yet**.
2. **Test Review Pause**:
   - Present the new/modified tests to the user, briefly explaining **what each test checks and why**.
   - **STOP and wait for user approval** before writing production logic (unless the user explicitly requested unattended end-to-end execution or the change has no executable tests).
3. **Green — Implement production code**:
   - Incorporate any feedback on tests, then implement the minimal code that passes the tests while honoring `AGENTS.md`:
     - **Onion architecture**: `domain/` is pure Kotlin (zero Android/Room/Karoo imports); `application/` orchestrates domain logic; `adapters/` & `extension/` wrap SDKs/IO; `framework/` & `ui/` handle DI, Room, and Compose.
     - **Kotlin style**: `when` over nested `if-else if`, scope functions (`let`, `run`, `apply`, `also`), named args where clarifying, small single-responsibility classes/functions (<40 lines), YAGNI, `$$"$..."` multi-dollar string syntax in app code, and imports at top of file (never inline FQNs).
     - **Android/Karoo specifics**: update `res/values-de/strings.xml` for every `res/values/strings.xml` change ([translations.md](../../rules/translations.md)); provide non-destructive Room `Migration` on DB schema changes; avoid 1 Hz SQLite/flash writes; preserve nullable defaults on `ForumsladerMetrics` data classes.
   - Verify with targeted `:app:testDebugUnitTest --tests ...`.
4. **Refactor & Final Check**:
   - Clean up duplication/nesting, ensure zero tests were weakened, and run `./gradlew :app:checkCoverageBaseline` once.

## 4. Hand-Off

Do **not** commit yet. Transition directly to [dev-review](../dev-review/SKILL.md) with the issue acceptance criteria, approved test summary, and worktree path.
