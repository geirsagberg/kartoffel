# AGENTS.md

- When asked to reproduce a bug/error in a test, assert the intended/wanted behavior (not the current buggy behavior), even if the test fails until the fix is implemented.
- Preserve meaningful existing comments; only remove or rewrite comments when they are clearly obsolete, incorrect, or redundant after code changes.
- This is a public repository: never commit secrets, API keys, signing keys, private location data, or local configuration values.

## Agent skills

### Issue tracker

Issues and PRDs live in GitHub Issues for `geirsagberg/kartoffel`. See `docs/agents/issue-tracker.md`.

### Triage labels

Use the default triage label vocabulary: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

This is a single-context repo with `CONTEXT.md` at the root and ADRs in `docs/adr/`. See `docs/agents/domain.md`.
