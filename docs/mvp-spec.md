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
- Recording Sessions interpolate only between consecutive accepted samples exactly two H3 steps apart. Every cell adjacent to both endpoints clears, so equally short local routes are retained; adjacent samples and broader jumps add no inferred coverage.
- H3 resolution is provisional until Android rendering is tested.

### Capture

- Tracking Mode is Off, Passive, or Recording Session. Passive Tracking is a durable opt-in; a Recording Session temporarily overrides it and stopping returns to Passive when that opt-in remains enabled, otherwise Off.
- Enabling Passive Tracking requires precise foreground and background location. Activity Recognition is optional and notification permission is not requested. Losing a required location permission switches Passive Tracking Off; unavailable recognition uses the safe fallback.
- Enabling Passive Tracking makes an initial high-accuracy request at 5 seconds until the first accepted fix, six delivered fixes, or 30 seconds. Rejected attempts remain evidence, and failure to acquire an accepted fix does not disable Passive Tracking.
- While Tracking Mode is Passive, a no-power Fused Location passive-priority subscription accepts fresh Opportunistic Fixes captured during the current Passive period. They use the normal accuracy gate, clear only their own Coverage Cell, do not open or extend a Passive Capture Window, and are suppressed during Recording Sessions.
- Fresh movement recognition opens a high-accuracy Passive Capture Window. A window collects at most six delivered fixes for at most 90 seconds, stops earlier on `STILL`, and requests 10 seconds while walking or `UNKNOWN`, 5 seconds while running or cycling, and 1 second in a vehicle. Rejected fixes consume the delivery limit.
- Activity Mode is aligned to the fix capture time when possible and otherwise stored as `UNKNOWN`. Live recognition state is not durable, resets to `UNKNOWN` after process recreation, and expires after 30 minutes without a fresh signal.
- Fresh `STILL` suppresses active passive windows. Unavailable or stale recognition permits an inexact, roughly hourly fallback window so denied recognition does not silently disable capture.
- Consecutive accepted passive fixes may use the existing short-gap Conservative Interpolation rule when their Coverage Cells have H3 grid distance 2, approximately 100 meters at the provisional resolution. Broader gaps remain Passive Gaps.
- Passive registrations recover after reboot, package replacement, and app launch without repeating the initial high-accuracy acquisition. Force-stop remains a platform boundary until the user opens Kartoffel again.
- Passive Tracking is best-effort and notification-free: Android background throttling may leave Passive Gaps. It does not keep a location foreground service running; Recording Sessions remain the reliable notification-backed fallback.
- Recording Sessions provide higher-fidelity start/stop capture with Conservative Interpolation between accepted samples.
- Recording Sessions start with a 5-second high-accuracy interval and wait for the first accuracy-gated fix before applying Activity Mode. This ensures starting a session while still can clear the current Coverage Cell. After that initial fix, sessions adapt to 10 seconds while walking, 5 seconds while running or cycling, and 1 second in a vehicle; a `STILL` transition suspends high-accuracy fixes until movement resumes. If no fix passes the accuracy gate within 30 seconds, the current Activity Mode takes effect to bound high-power startup work.
- Recording Session start also requests 1-second Activity Recognition samples for at most 15 seconds. While the mode is `UNKNOWN`, the first supported Activity Mode with at least 75% confidence seeds it and stops sampling immediately; later queued samples are ignored. The low-power Transition API remains active and takes precedence if it reports a mode first, while timeout or weak classifications leave the mode `UNKNOWN`.
- Reported speed may only escalate polling: at least 2.5 m/s requests 5 seconds and at least 10 m/s requests 1 second. Activity Recognition denial or failure leaves the safe 5-second fallback active.
- Both sources feed the same clearing and persistence pipeline with source-aware triggers and rules. Passive and Recording Session fixes share high-accuracy requests and the 20-meter gate; their duty cycle and lifecycle differ.

### Data

- Room stores Coverage Cells, Location Samples, Recording Sessions, session geometry, and settings.
- Interpolated coverage is stored only as Coverage Cells; observed Location Samples and Recording Session Points remain the reviewable evidence trail.
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
