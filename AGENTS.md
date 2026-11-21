# Karoo Forumslader — Agent Guide

## References

- **Review & coding standards**: Read [CODING_STANDARDS.md](CODING_STANDARDS.md) when writing or reviewing code (onion architecture, Kotlin style, testing, and commit/PR rules).
- **Roadmap & priorities**: Read [docs/roadmap.md](docs/roadmap.md) when planning or prioritizing improvements.
- **Forumslader BLE protocol**: Read [.agents/skills/forumslader-protocol/SKILL.md](.agents/skills/forumslader-protocol/SKILL.md) and [docs/ble/flt-commands.md](docs/ble/flt-commands.md) when changing telemetry parsing, `$FLT` commands, or status bitmask alerts.
- **Battery range estimation**: Read [.agents/skills/battery-range-estimation/SKILL.md](.agents/skills/battery-range-estimation/SKILL.md) and [docs/estimation/battery-range-estimation.md](docs/estimation/battery-range-estimation.md) when changing `domain/estimation/` or `application/estimation/`.
- **Translations**: Read [.agents/rules/translations.md](.agents/rules/translations.md) whenever adding or editing `app/src/main/res/values/strings.xml`.
- **GitHub issues**: Read [.agents/rules/github_issues.md](.agents/rules/github_issues.md) when drafting a GitHub issue.

## Verification

- **Fast inner loop**: `./gradlew :app:testDebugUnitTest`
- **Final CI gate** (unit tests + Kover line-coverage baseline): `./gradlew :app:checkCoverageBaseline`
