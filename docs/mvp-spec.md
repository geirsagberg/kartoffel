# Kartoffel MVP

## Goal

Test whether opt-in Passive Tracking can produce Good Enough History with Barely Noticeable Battery Use. A Recording Session remains the reliable fallback for routes the user cares about.

The product language and principles live in [`CONTEXT.md`](../CONTEXT.md). Architectural choices live in [`docs/adr/`](adr/).

## Experience

Kartoffel opens to a full-screen Coverage Map that centers when location becomes available. Fog of War hides unvisited Coverage Cells. The main map offers a quiet Passive Tracking toggle and a start/stop Recording Session control; settings and Tracking Diagnostics stay secondary.

Permissions are requested in context: foreground location for map/session use, and background location plus any required notification/foreground-service permissions when Passive Tracking is enabled.

## Required behavior

### Coverage

- H3 Coverage Cells are the durable truth; rendered fog tiles are disposable.
- Accuracy-Gated Clearing and Conservative Interpolation prefer Passive Gaps over False Coverage.
- Recording Sessions may trust short plausible gaps more than Passive Tracking.
- Long gaps and implausible movement must not clear straight-line routes.
- H3 resolution is provisional until Android rendering is tested.

### Capture

- Passive Tracking is explicit opt-in and uses bounded, opportunistic Android location capture rather than a constant high-power stream.
- Recording Sessions provide higher-fidelity start/stop capture.
- Both sources feed the same clearing and persistence pipeline with source-aware rules.

### Data

- Room stores Coverage Cells, Location Samples, Recording Sessions, session geometry, and settings.
- Coverage Cells retain canonical H3 ID plus first/last-seen times and minimal evidence.
- Passive samples may be compressed after a fixed window without losing coverage.
- Recording Sessions keep a simplified reviewable route by default.

### Diagnostics

Tracking Diagnostics should make tuning decisions inspectable: sample source/trigger, acceptance or rejection reason, accuracy, cleared cells, meaningful passive gaps, and useful battery signals available without heavy instrumentation.

## Implementation boundaries

- Native Kotlin/Compose app targeting Android 16 only.
- Google Maps with an app-owned fog tile overlay.
- Google Play services Fused Location Provider.
- Room/SQLite and direct H3 integration.
- One Android app module with packages for `tracking`, `coverage`, `map`, `storage`, `settings`, and `diagnostics` as needed.

Do not add accounts, cloud sync, backup/restore, import/merge, offline maps, social features, manual clearing, workout features, multiple Gradle modules, alternative spatial indexes, or persistent precomputed fog tiles during MVP.

## Verification

Automate behavior at stable seams:

- sample acceptance, rejection, and resulting Coverage Cells;
- interpolation across plausible and implausible gaps;
- source-specific clearing behavior;
- retention without coverage loss;
- fog tile output for known cells;
- Room persistence and migrations;
- tracking orchestration behind Android API wrappers.

Use the Android 16 device for H3 packaging, Maps overlay performance, permission and foreground-service behavior, passive coverage quality, and battery impact.
