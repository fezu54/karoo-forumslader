# Contributing to Karoo Forumslader

Thank you for contributing to Karoo Forumslader! To maintain code quality and support automated releases, please follow these guidelines.

---

## Commit & PR Title Conventions (Conventional Commits)

This repository relies on automated semantic versioning and release generation driven by commit messages on `main`. Because pull requests are squash-merged, **PR titles become the commit message on `main`**.

All commit messages and PR titles must strictly follow the [Conventional Commits](https://www.conventionalcommits.org/) specification:

```text
<type>(<optional-scope>): <description>
```

### Allowed Types

| Type | Description | Automated Release |
| :--- | :--- | :--- |
| `feat` | A new user-facing feature | **Minor** release (`vX.Y+1.0`) |
| `refactor` | Code changes that neither fix a bug nor add a feature | **Minor** release (`vX.Y+1.0`) |
| `fix` | A bug fix | **Patch** release (`vX.Y.Z+1`) |
| `docs` | Documentation changes only | None |
| `style` | Formatting or white-space changes | None |
| `perf` | Performance improvements | None |
| `test` | Adding or updating tests | None |
| `build` | Changes to build configuration or dependencies | None |
| `ci` | Changes to CI workflows or scripts | None |
| `chore` | Routine maintenance or internal tasks | None |
| `revert` | Reverting a previous commit | None |

> [!IMPORTANT]
> - Type prefixes must be strictly **lowercase** (e.g. `feat:`, `fix:`, never `Feat:` or `Fix:`).
> - The description may start with either a lowercase or uppercase letter (e.g., `feat: add gauge` or `feat: Add gauge`).

### Breaking Changes

To trigger a **Major** release (`vX+1.0.0`), append an exclamation mark `!` before the colon:

```text
refactor(comm)!: redesign Forumslader BLE protocol
```

Or include `BREAKING CHANGE:` in the commit/PR description body.

---

## Local Git Hook (Android Studio & CLI)

To catch non-compliant commit messages before pushing, this repository includes a pre-configured `commit-msg` hook located in `.githooks/`.

### Automatic Hook Setup

The hook is configured automatically whenever you:
- Open or sync the project in **Android Studio / IntelliJ IDEA**
- Build or test the project with Gradle (`./gradlew ...`)
- Start the project inside a **VS Code Dev Container**

You can also manually configure the hook at any time by running:
```bash
./gradlew installGitHooks
```
or via Git directly:
```bash
git config core.hooksPath .githooks
```

### Android Studio Compatibility

The hook is fully compatible with Android Studio:
- Git template comments (lines starting with `#`) and empty lines are stripped automatically before evaluating the subject.
- Non-conforming commits are aborted cleanly, and an informative rejection message is displayed directly in the Android Studio Commit UI dialog.

---

## Pull Request Guidelines

1. **PR Title**: Ensure your PR title adheres strictly to the Conventional Commits format (e.g. `feat(ui): redesign main cards layout`).
2. **Automated Validation**: CI verifies PR titles via `amannn/action-semantic-pull-request`. If the check fails, edit your PR title on GitHub and the check will automatically re-run.
3. **Tests**: All changes must include unit tests. Ensure existing and new tests pass before submitting.
