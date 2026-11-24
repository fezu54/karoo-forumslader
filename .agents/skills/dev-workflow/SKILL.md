---
name: dev-workflow
description: >-
  Master development workflow orchestrating the AI factory for karoo-forumslader:
  issue verification and planning, TDD implementation in an isolated worktree,
  iterative independent code review (max 3 rounds), and why-focused Conventional
  Commits. Use when asked to implement a feature, refactor code, fix a bug, or
  make any non-trivial repository change.
---

# Karoo Forumslader AI Development Factory

Execute software changes through four sequential stages. Do not skip stages or reorder gates.

| Stage | Skill | Gate / Exit Condition |
| :--- | :--- | :--- |
| 1. Issue & Clarify | [dev-issue-gate](../dev-issue-gate/SKILL.md) | Issue verified (or small isolated bug fix confirmed) AND zero open ambiguities |
| 2. TDD & Implement | [dev-implement](../dev-implement/SKILL.md) | Worktree isolated, tests approved by user first, implementation + single `:app:checkCoverageBaseline` pass |
| 3. Review Loop | [dev-review](../dev-review/SKILL.md) | Independent review returns `VERDICT: PASS` within max 3 rounds |
| 4. Deliver | [dev-deliver](../dev-deliver/SKILL.md) | Why-only Conventional Commit (+ optional PR and worktree cleanup) |

## Lazy Domain Skill Routing

Load specialized domain skills **only** when the task touches those subsystems (saves context tokens):

- **Battery range estimation, gradient learning, headwind, or FIT energy balance** (`domain/estimation/`, `application/estimation/`): read [battery-range-estimation](../battery-range-estimation/SKILL.md).
- **Forumslader BLE sentences, bitmasks, scaling, or `$FLT` commands** (`ForumsladerParser`, `ForumsladerProtocol`, `ForumsladerVersion`, alert rules): read [forumslader-protocol](../forumslader-protocol/SKILL.md).

## Non-Negotiable Cross-Stage Invariants

1. **No issue, no feature**: Never write code or create a worktree for a feature or significant change until a verified GitHub issue exists (`dev-issue-gate`).
2. **Never invent requirements**: Stop and ask concise questions whenever acceptance criteria, edge cases, UX copy, or architecture choices are unclear.
3. **TDD with test review**: Write unit tests first and ask the user to review them before writing production code.
4. **Token-saving discipline**: Never run Gradle before editing files, never unpack `.aar`/`.jar` archives, and avoid redundant full-file reads.
5. **Bounded review loop**: Every implementation passes through `dev-review` for at most 3 rounds before committing; escalate to the user if blockers persist after Round 3.
6. **Why-only commits**: Commit bodies explain why in plain language and never enumerate changed files or narrate `git diff`.
