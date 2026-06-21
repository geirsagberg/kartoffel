# Kartoffel

Kartoffel is a personal location history app centered on remembering where the user has been and showing that history spatially.

## Language

**Fog of War**:
A personal map overlay that obscures places outside the user's street-level coverage and reveals places the user has visited.
_Avoid_: Coverage highlight, explored map

**Coverage Map**:
The main product surface where the user sees their street-level coverage and the remaining fog of war.
_Avoid_: Dashboard, tracker screen

**Street-Level Coverage**:
The user's visited map detail at roughly streets, blocks, and small urban areas, rather than countries, cities, or broad regions.
_Avoid_: Country visits, city visits, travel history

**Coverage Cell**:
A small area of the map that can be marked as visited for the purpose of street-level coverage.
_Avoid_: Street segment, path buffer

**False Coverage**:
A coverage cell marked as visited even though the user did not pass near it.
_Avoid_: Generous clearing, optimistic coverage

**Accuracy-Gated Clearing**:
Marking coverage cells as visited only when location evidence is accurate enough for the capture source and fog-of-war purpose.
_Avoid_: Accuracy radius clearing, broad clearing

**Conservative Interpolation**:
Filling coverage between nearby location samples only when the gap, speed, accuracy, and capture source make the movement plausible.
_Avoid_: Straight-line clearing, route guessing

**Passive Gap**:
A real place the user visited that remains under fog of war because passive tracking did not capture enough evidence.
_Avoid_: Tracking failure, missing route

**Manual Clearing**:
A user-initiated correction that marks fog of war as visited when automatic location capture missed enough of the user's real movement.
_Avoid_: Manual tracking, drawing paths

**Coverage Evidence**:
Information retained about why a coverage cell is considered visited, such as whether it came from deliberate or passive location capture.
_Avoid_: Confidence score, coverage metadata

**Evidence Retention**:
The user's control over how long the app keeps the information behind street-level coverage before deleting or reducing it.
_Avoid_: Data cleanup, storage management

**Evidence Compression**:
Reducing retained location evidence while preserving the street-level coverage and useful location history it already supports.
_Avoid_: Path buffering, geometry simplification

**Minimal by Default**:
The product principle that the main experience should stay quiet and focused, with detail and customization available only when the user asks for it.
_Avoid_: Dashboard, cockpit, power-user first

**Local-First**:
The product principle that the app can be useful without an account and that the user's location history is controlled from the device where it is captured.
_Avoid_: Account-first, cloud-first

**Location History**:
The user's remembered past positions over time, regardless of how those positions were captured.
_Avoid_: Tracks, logs

**Good Enough History**:
Location history that is complete enough to make the coverage map useful without requiring the user to remember to start a recording session, while avoiding obvious battery cost.
_Avoid_: Perfect history, exhaustive tracking

**Barely Noticeable Battery Use**:
The product expectation that passive tracking should not create obvious battery anxiety during normal daily use.
_Avoid_: Battery-neutral, free tracking

**Recording Session**:
A deliberate period of location capture that the user starts because they want reliable, higher-fidelity location history.
_Avoid_: Trip, workout, activity

**Passive Tracking**:
Location capture that can happen without the user explicitly starting a recording session, intended to reduce missed location history.
_Avoid_: Background recording, always-on tracking

**Background Capture**:
Location capture that continues while the app is not visible, including cases where Android requires an ongoing notification.
_Avoid_: Invisible tracking, hidden tracking

**Tracking Mode**:
A user-facing choice that balances battery use, coverage detail, and retained location evidence without exposing every underlying tuning control.
_Avoid_: Precision profile, sampling configuration

**Tracking Diagnostics**:
A hidden or secondary view of how location evidence becomes street-level coverage, used to tune battery use and coverage detail without cluttering the main experience.
_Avoid_: Debug screen, logs
