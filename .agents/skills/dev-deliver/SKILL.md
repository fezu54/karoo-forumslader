---
name: dev-deliver
description: >-
  Creates Conventional Commits with plain-language why-only commit bodies, opens
  optional Pull Requests, and manages worktree cleanup after dev-review passes.
  Use when committing changes, preparing PRs, or finishing a worktree task.
---

# Stage 4: Why-Only Conventional Commits & Delivery

Enter this stage only after [dev-review](../dev-review/SKILL.md) returns `VERDICT: PASS`.

## 1. Staging Hygiene & Hook Check

Inside the active worktree (or branch):
1. Inspect `git status` and `git diff --stat`. Stage **only** files belonging to this task — never stage unrelated untracked files or `.cache`/local artifacts.
2. Verify the repo hook is active (`git config core.hooksPath`); if unset, run `git config core.hooksPath .githooks` (enforces `.githooks/commit-msg`).

## 2. Conventional Commit Format

Follow [CONTRIBUTING.md](../../../CONTRIBUTING.md) and `.githooks/commit-msg`:

```text
<type>(<optional-scope>): <short description>

<why-only body in plain, easy-to-understand language>

Closes #<issue-number>
```

### Allowed Types & CI Release Impact (`ci.yml`)

| Type | Automated Release on `main` |
| :--- | :--- |
| `feat`, `refactor` | **Minor** (`vX.Y+1.0`) |
| `fix` | **Patch** (`vX.Y.Z+1`) |
| Append `!` before `:` or `BREAKING CHANGE:` in footer | **Major** (`vX+1.0.0`) |
| `docs`, `style`, `perf`, `test`, `build`, `ci`, `chore`, `revert` | None |

Type prefix MUST be strictly lowercase (`feat:`, `fix:`, never `Feat:`).

### Strict Body Rules ("Why", Never "What")

- **Explain WHY** directly in plain, everyday language (no `Why:` or `## Why` heading needed): what problem existed, how users or hardware were affected, or why this trade-off/design was chosen.
- **NEVER list changed files** (`Modified Foo.kt`, `Added BarTest.kt`).
- **NEVER narrate code edits** (`Added method X`, `Extracted class Y`, `Updated loop Z`) — that is what `git diff` is for.

**Good body:**
> When USB charging is disabled to prioritize the headlight, riders previously had to re-enable USB manually once the light turned off. Restoring the prior USB state automatically removes that manual step without interfering when the rider disabled USB on purpose.

**Bad body:**
> - Modified `ForumsladerKarooAdapter.kt` to add `autoDisabledUsbByLight`.
> - Updated `evaluateLightLoad()` to send `SetConsumerState(enabled = true)`.
> - Added unit tests in `ForumsladerKarooAdapterTest.kt`.

Use `Closes #<N>` when the change resolves an issue, or `Related to #<N>` for partial progress.

## 3. Optional Pull Request & Worktree Cleanup

1. **Confirm with user** before pushing or creating a PR (unless already instructed).
2. Remind/ensure the **PR title** itself follows Conventional Commits because squash-merges turn the PR title into the `main` commit and `.github/workflows/pr-lint.yml` enforces it.
3. Keep the PR body to the plain-language explanation of why the change is needed plus an optional issue reference (`Closes #<N>` / `Related to #<N>`). Do **not** add a `Why` / `## Why` heading, a `Verification` section, or a file-by-file list.
4. **Offline fallback**: If `gh pr create` cannot reach GitHub, print the ready-to-use PR title and body and stop cleanly.
5. **Worktree cleanup**: Once pushed/merged (or discarded on user request), offer to remove the linked worktree from the main repository root:
   ```bash
   git worktree remove .worktrees/<slug> && git worktree prune
   ```
   Never delete a worktree containing unpushed/unmerged commits without explicit user approval.
