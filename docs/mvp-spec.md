# Kartoffel MVP Spec

## Problem Statement

Kartoffel should help the user see which streets, blocks, and small urban areas they have actually visited, without requiring them to remember to start tracking every time they go outside.

The core product risk is whether passive tracking can create Good Enough History with Barely Noticeable Battery Use. The MVP exists to test that risk while still providing a reliable manual Recording Session path when the user cares about a walk.

## Product Principles

- Minimal by Default: the main experience is a quiet Coverage Map, not a dashboard.
- Local-First: no account, no cloud sync, and location history remains controlled from the device.
- Passive Tracking is first-class: it is part of MVP, not a later add-on.
- False Coverage is worse than a Passive Gap: the app should be conservative when evidence is weak.
- Details stay available through Tracking Diagnostics, not through cluttering the main UI.

## MVP Solution

The app opens to a full-screen Coverage Map. The map automatically centers once location is found, shows current location, and renders Fog of War over unexplored areas. Passive Tracking can be explicitly enabled and should quietly collect enough location evidence to clear meaningful Street-Level Coverage without causing obvious battery anxiety. A minimal start/stop Recording Session control provides higher-fidelity capture for walks the user cares about.

## Core User Stories

1. As the primary user, I want to open the app and immediately see my Coverage Map, so that I can understand what nearby areas are still under Fog of War.
2. As the primary user, I want the map to center automatically once location is found, so that the first screen is useful without fiddling.
3. As the primary user, I want Passive Tracking to clear areas I plausibly visited during normal movement, so that I do not have to remember to start a session every time.
4. As the primary user, I want Passive Tracking to use barely noticeable battery, so that I can leave it enabled without battery anxiety.
5. As the primary user, I want sparse passive data to be conservative, so that the app does not create False Coverage.
6. As the primary user, I want to start a Recording Session when I care about a specific walk, so that the app captures more reliable Street-Level Coverage.
7. As the primary user, I want Recording Sessions to keep a simplified reviewable route, so that intentional captures remain useful after compression.
8. As the primary user, I want passive evidence to compress over time, so that long-term storage stays reasonable.
9. As the primary user, I want simple tracking settings, so that tuning does not turn the app into a cockpit.
10. As the primary user, I want hidden Tracking Diagnostics, so that I can understand why areas were or were not cleared.
11. As the primary user, I want location permission requests to appear in context, so that the app only asks for sensitive access when a feature needs it.

## MVP Scope

### Coverage Map

- Full-screen Google Maps view.
- Modern Material Design defaults with minimal custom styling.
- Current-location control.
- Automatic centering once location is found.
- Subtle passive tracking status/toggle.
- Simple Recording Session start/stop control.
- Settings and Tracking Diagnostics behind a small menu or sheet.

### Fog of War

- Fog of War is part of MVP.
- Fog obscures places outside Street-Level Coverage and reveals Coverage Cells the user has visited.
- Render Fog of War through a custom Google Maps tile overlay.
- Generate fog tiles for the current viewport only.
- Keep fog tiles disposable: Coverage Cells are permanent truth; rendered tiles are cacheable pixels.
- Avoid precomputed persistent fog tile storage in MVP.

### Passive Tracking

- Explicit opt-in.
- May use an Android-required ongoing notification when enabled.
- Target Barely Noticeable Battery Use as the default constraint.
- Use a hybrid opportunistic strategy:
  - passive fused location updates;
  - activity transition triggers;
  - bounded current-location requests after likely movement;
  - geofence-style movement triggers if useful;
  - batched low-power updates if other signals are too sparse.
- Prefer Passive Gaps over False Coverage.
- Default passive tracking may miss short walks.

### Recording Sessions

- Minimal start/stop capture mode.
- Higher-fidelity than Passive Tracking.
- Used as both a user feature and a comparison baseline for passive capture quality.
- No workout stats, speed charts, or activity feed in MVP.

### Clearing Rules

- Coverage uses H3 Coverage Cells.
- Spike H3 resolutions 12 and 13, then choose the coarsest resolution that still feels street-level.
- Use Accuracy-Gated Clearing.
- Use Conservative Interpolation:
  - Recording Sessions can interpolate across short plausible gaps.
  - Passive Tracking interpolates only across very short plausible gaps, or not at all initially if tuning suggests False Coverage risk.
  - Long gaps and transit-speed jumps should not clear straight-line routes.
- Store first and last seen timestamps for Coverage Cells.

### Retention and Compression

- MVP uses simple retention/compression rules.
- Recording Sessions keep simplified routes long-term by default.
- Passive Tracking evidence compresses after a fixed window into Coverage Cells plus minimal Coverage Evidence.
- No density heuristics, no storage-pressure policy beyond warning if needed.
- No Manual Clearing in MVP, but the domain allows it as a later repair tool.

### Tracking Diagnostics

Diagnostics should explain why the app cleared or did not clear Fog of War:

- samples by source and trigger;
- accepted vs rejected samples;
- rejection reason;
- accuracy distribution;
- cells cleared per sample;
- gaps between accepted samples;
- rough battery impact if useful signal is available.

Diagnostics are not a general log console.

## Implementation Decisions

- Native Android app.
- Android 16 only.
- Kotlin and dependencies should be latest stable at implementation time.
- Kotlin + Jetpack Compose for app UI.
- Use Google Maps SDK for the map.
- Use Maps Compose if it supports the required overlay cleanly; otherwise bridge to `MapView`.
- Use Google Play services Fused Location Provider for location capture.
- Use Room on SQLite as the primary local store.
- Use H3 directly for Coverage Cells.
- Do not build a preemptive S2/custom-grid fallback abstraction.
- Single Android app module for MVP.
- Use clear packages rather than Gradle feature modules:
  - `tracking`
  - `coverage`
  - `map`
  - `storage`
  - `settings`
  - `diagnostics`

## MVP Data Model

Persist five core concepts:

- `CoverageCell`: canonical H3 cell ID, first seen time, last seen time, source/evidence summary.
- `LocationSample`: timestamp, latitude, longitude, accuracy, source/trigger, accepted/rejected state, rejection reason.
- `RecordingSession`: start time, end time, status.
- `SessionPoint` or simplified session geometry: points/shape tied to a Recording Session.
- `AppSetting`: passive tracking enabled, Tracking Mode, retention choices.

For MVP, store one row per canonical H3 Coverage Cell. Do not start with compacted H3 blobs.

## Permissions

Use progressive permission onboarding:

- Request foreground precise location when the map/current location feature needs it.
- Request background location when Passive Tracking is enabled.
- Request notification permission when the ongoing tracking notification is needed.
- Ensure foreground service location requirements are satisfied before background capture or Recording Sessions rely on them.

## Testing Decisions

Prefer tests at behavior seams, not implementation details:

- Coverage clearing: given samples with timestamps, accuracy, and source, assert resulting Coverage Cells and rejection reasons.
- Conservative interpolation: assert short plausible gaps clear, long or implausible gaps do not.
- Passive vs Recording Session rules: assert source-specific thresholds differ where intended.
- Retention/compression: assert passive evidence can compress without losing Coverage Cells.
- Fog tile generation: given a viewport/tile and Coverage Cells, assert the tile has fog where expected and transparent cleared areas where expected.
- Storage migrations/entities: use Room tests once schema exists.
- Tracking pipeline: isolate Android API wrappers so trigger handling can be tested without device location services.

Manual testing on the Android 16 device remains required for:

- actual battery impact;
- background permission behavior;
- notification/foreground-service behavior;
- H3 Android stability;
- Google Maps tile overlay performance.

## Out of Scope

- Account system.
- Cloud sync.
- General import/merge.
- Backup/restore in MVP.
- Offline base maps.
- User-facing history/calendar view.
- Manual Clearing.
- Multi-device support.
- Social/sharing features.
- Detailed tracking modes such as Battery Saver or High Detail.
- DuckDB, GeoPackage, or a geospatial database.
- Precomputed persistent fog tiles.
- Multiple Gradle modules.

## Known Spikes

- Verify H3 works reliably on the Android 16 device.
- Compare H3 resolutions 12 and 13 for street-level feel and render cost.
- Verify Maps Compose can support the required Fog of War tile overlay; otherwise use `MapView`.
- Measure passive tracking battery impact with real daily use.
- Tune passive triggers against Recording Sessions and Tracking Diagnostics.
