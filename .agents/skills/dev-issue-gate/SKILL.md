---
name: dev-issue-gate
description: >-
  Verifies whether a GitHub issue exists before starting features or significant
  changes in karoo-forumslader, clarifies ambiguous requirements without inventing
  scope, and drafts human-readable implementation plans for approval. Use at the
  start of any development task before creating worktrees or editing code.
---

# Stage 1: Issue Gate & Requirement Clarification

Verify scope and requirements **before** creating a worktree or modifying files.

## 1. Classify the Request

| Request Category | Issue Required? | Examples |
| :--- | :--- | :--- |
| Feature or significant change | **Yes** | New data fields, UI cards/settings, refactors, protocol/estimation changes, Room schema migrations, multi-component edits, behavioral trade-offs |
| Small isolated change | **No** | Single-cause bug fix with obvious expected behavior, typo/translation fix, test-only repair |

When uncertain whether a change is significant, require an issue or ask the user first.

## 2. Never Invent Requirements (Clarification Checklist)

Before drafting an issue or writing code, check for gaps across:
- **User-visible behavior**: exact UI strings, units, thresholds, error/empty states, German translation nuances.
- **Edge cases & lifecycle**: BLE disconnect, `RideState` transitions, null/missing fields, out-of-range telemetry.
- **Hardware / protocol assumptions**: V5 vs V6 behavior, `$FL*` fields, `$FLT` command timing.
- **Persistence & config**: defaults, SharedPreferences keys, Room `Migration` behavior, user-configurable vs fixed values.
- **Scope boundaries**: what is explicitly in or out of this change.

If anything is underspecified, **stop and ask concise questions** (use `ask_question` when discrete options clarify intent) before proceeding.

## 3. Verify GitHub Issue

- If the user provided `#N`, fetch it: `gh issue view <N> --json number,title,state,body,url`.
- Otherwise, check open issues: `gh issue list --state open --limit 30 --json number,title,labels,url`.

### Decision Paths

1. **Exempt small isolated bug fix** -> Clarify any ambiguities, then hand off to [dev-implement](../dev-implement/SKILL.md).
2. **Matching open issue found (and acceptance criteria clear)** -> Summarize the issue `#N` and acceptance criteria, clarify any remaining gaps, then hand off to `dev-implement`.
3. **No matching issue for a feature / significant change**:
   - **Decline implementation**: Do not create a worktree, branch, or code edits. Explain briefly that this change requires a GitHub issue first.
   - Ask clarifying questions for any missing requirements first.
   - Draft the complete issue strictly following [github_issues.md](../../rules/github_issues.md):
     - `## Context` (problem, motivation, optional `**Blocked By:** #<id>`).
     - `## Implementation Plan` (human-readable breakdown by onion ring/component, key design choices, migration/risk notes, and test strategy).
     - `## Acceptance Criteria` (`- [ ]` testable requirements).
   - **If GitHub is unreachable** (neither `gh` nor API works): present the full Markdown draft issue + implementation plan in your response, note that GitHub could not be reached, and **STOP**.
   - **If GitHub is reachable**: present the draft to the user for review and wait for explicit approval. Only after approval, run `gh issue create --title "..." --body "..."`, share the created URL/number, and wait for the user's go-ahead to implement (unless the user already asked to create and start immediately).
4. **Offline while user cited `#N` without details**: Do not guess requirements. Ask the user to paste the issue description / acceptance criteria before continuing.
