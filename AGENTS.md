# AGENTS.md

- Bug-reproduction tests must assert intended behavior, even if they fail until fixed.
- Preserve meaningful comments; change or remove only those made obsolete, incorrect, or redundant.
- Prefer Kotlin extension (KTX) APIs where available and appropriate.
- Preserve existing on-device app data across updates where feasible. Before introducing a destructive migration, signing/application-ID change, or other upgrade path that would discard it, ask the user unless they explicitly authorized the loss.
- Never install, uninstall, clear data, or run connected tests on an attached device without explicit authorization; use an emulator by default.
- This is a public repository: never commit secrets, API keys, signing keys, private location data, or local configuration values.

## Emulators

Parallel threads share this machine's emulators, and every worktree installs the same app ID.

- Run connected tests with `./gradlew :app:pixel9Api36DebugAndroidTest`. Gradle boots a private emulator for the run and shuts it down afterwards.
- To show the app to the user, use the harness's device panel when available. Otherwise start a windowed emulator on a free even port from 5556 (`emulator -avd <name> -read-only -port <port>`) and set `ANDROID_SERIAL=emulator-<port>` for adb and Gradle. Shut it down when the user is done.
- Treat an emulator you did not start as another thread's; start your own.

## Agent skills

### Issue tracker

Issues and specs live in GitHub Issues for `geirsagberg/kartoffel`. See `docs/agents/issue-tracker.md`.
Before running any `gh` operation, ensure the active GitHub account is `geirsagberg`; if needed, run `gh auth switch --hostname github.com --user geirsagberg` first.

### Triage labels

Use the default triage label vocabulary: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

This is a single-context repo with `CONTEXT.md` at the root and ADRs in `docs/adr/`. See `docs/agents/domain.md`.
