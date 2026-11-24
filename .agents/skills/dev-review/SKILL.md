---
name: dev-review
description: >-
  Runs an iterative, read-only code review and fix loop (maximum 3 rounds) after
  implementation in karoo-forumslader. Checks acceptance criteria, AGENTS.md rules,
  onion architecture, German translations, and coverage before committing.
---

# Stage 3: Independent Review & Fix Loop (Max 3 Rounds)

Run this stage after [dev-implement](../dev-implement/SKILL.md) finishes and `:app:checkCoverageBaseline` passes, **before** creating any commit. Track `round = 1` (hard limit: `MAX_ROUNDS = 3`).

## 1. Execute Independent Review

- **Preferred (Subagent-capable tools)**: Spawn a fresh **read-only reviewer subagent** (`research` in Antigravity CLI, or a read-only review subagent in Claude Code) so review runs with zero author bias and minimal context overhead.
- **Fallback (No subagent tool available)**: Perform a strict self-review pass on `git status` and `git diff` using the exact same rubric and output format.

### Compact Subagent Brief

Pass **only**:
1. Worktree/repository directory path and current review `round` (`1..3`).
2. Issue `#N` acceptance criteria (or bug fix goal) + approved test summary.
3. Instruction to run `git status` and `git diff` (plus inspect modified/added files) and return the structured verdict below against the rubric.

## 2. Review Rubric

1. **Scope & Tests**: All acceptance criteria satisfied; approved TDD tests are intact (never weakened, skipped, or deleted just to pass); no invented requirements or over-engineering (YAGNI).
2. **Correctness & Lifecycle**: Edge cases and nullability handled cleanly; `Emitter` / `ViewEmitter` `setCancellable`, `karooSystem.removeConsumer`, and coroutines leak-free; non-destructive Room `Migration` if DB schema changed.
3. **Architecture & Kotlin Style (`AGENTS.md`)**: Pure Kotlin `domain/` (no Android/Room/Karoo imports); inner layers never depend on outer layers; OOP single-purpose classes/functions (<40 lines); `when` over nested `if-else if`; scope functions; top-of-file imports (no inline FQNs); `$$"$..."` in app Kotlin files.
4. **Translations**: Every addition/edit in `app/src/main/res/values/strings.xml` has a matching entry in `app/src/main/res/values-de/strings.xml` ([translations.md](../../rules/translations.md)).
5. **Verification**: Deterministic tests with `` `should ... when ...` `` names and `./gradlew :app:checkCoverageBaseline` passed after the last code edit.
6. **Domain Invariants**: If touching `estimation/` or `$FL*` parsing/commands, verify invariants from `battery-range-estimation` / `forumslader-protocol`.

## 3. Verdict Format

```text
VERDICT: PASS | CHANGES_REQUESTED
BLOCKING:
- [file:line] Concrete defect -> minimal fix
NON_BLOCKING:
- [file:line] Optional improvement
```

## 4. Loop Controller

- **`VERDICT: PASS`**: Hand off immediately to [dev-deliver](../dev-deliver/SKILL.md).
- **`VERDICT: CHANGES_REQUESTED` with `round < 3`**:
  1. Fix every **BLOCKING** item cleanly (do not invent extra scope or weaken approved tests).
  2. Re-run targeted unit tests, then run `./gradlew :app:checkCoverageBaseline` once.
  3. Increment `round` and start a fresh review subagent pass.
- **`VERDICT: CHANGES_REQUESTED` with `round == 3`**:
  - **STOP immediately.** Do not start a 4th round and do not commit. Report the unresolved blocking findings and current verification state to the user.
