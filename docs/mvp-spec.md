# Kartoffel MVP

## Goal

Test whether opt-in Passive Tracking can produce Good Enough History with Barely Noticeable Battery Use. A Recording Session remains the reliable fallback for routes the user cares about.

The product language and principles live in [`CONTEXT.md`](../CONTEXT.md). Architectural choices live in [`docs/adr/`](adr/).

## Experience

Kartoffel opens to a Coverage Map that centers when location becomes available. This initial foreground fix is used only for map positioning and does not clear coverage. Deliberate Recording Sessions accuracy-gate their samples and clear accepted Coverage Cells; inaccurate fixes remain covered. Fog of War hides all other unvisited cells. A compact app bar keeps Passive Tracking status and the start/stop Recording Session control visible; settings and Tracking Diagnostics stay in its overflow menu. The map retains an edge-to-edge current-location control.

Permissions are requested in context: foreground location for map/session use, and background location plus any required notification/foreground-service permissions when Passive Tracking is enabled.

## Required behavior

### Coverage

- H3 Coverage Cells are the durable truth; rendered fog tiles are disposable.
- Coverage Compaction may store complete H3 child sets as their parent, while preserving the same logical resolution-11 coverage.
- The stored set is canonical: no cell is stored alongside one of its ancestors, complete child sets compact recursively, and geometric rendering expands compacted cells to resolution 11.
- Accuracy-Gated Clearing prefers Passive Gaps over clearly implausible coverage.
- Conservative Interpolation may fill short adjacency gaps between credible samples, including every equally short local route; broader interpolation remains deferred.
- H3 resolution is provisional until Android rendering is tested.

### Capture

- Passive Tracking is explicit opt-in and uses bounded, opportunistic Android location capture rather than a constant high-power stream.
- Recording Sessions provide higher-fidelity start/stop capture with Conservative Interpolation between accepted samples.
- Both sources feed the same clearing and persistence pipeline with source-aware rules.

### Data

- Room stores Coverage Cells, Location Samples, Recording Sessions, session geometry, and settings.
- Upgrading from the pre-session schema resets local data so cells cleared by the retired foreground-fix behavior do not survive.
- Coverage Cells retain canonical H3 IDs plus first/last-seen times and minimal evidence. A compacted parent aggregates its children with earliest first-seen time, latest last-seen time, and the union of evidence sources.
- Passive samples may be compressed after a fixed window without losing coverage.
- Recording Sessions keep a reviewable route simplified to Coverage Cell transitions by default.

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
- recording samples clear only their own Coverage Cells;
- source-specific clearing behavior;
- retention without coverage loss;
- fog tile output for known cells;
- Coverage Compaction round-trips to the same resolution-11 Coverage Cells and renders the same cleared area;
- Room persistence and migrations;
- tracking orchestration behind Android API wrappers.

Use the Android 16 device for H3 packaging, Maps overlay performance, permission and foreground-service behavior, passive coverage quality, and battery impact.
