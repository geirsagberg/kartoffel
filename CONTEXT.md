# Kartoffel

Kartoffel is a local-first Android app that remembers where its user has been and reveals that history on a **Coverage Map**.

## Product principles

- **Minimal by Default:** keep the main experience focused; put tuning and detail in secondary surfaces.
- **Local-First:** work without an account and keep location history under device-local control.
- **Clearly False Coverage is worse than a Passive Gap:** short, locally plausible ambiguity may clear every credible route; implausible jumps should leave fog in place.
- **Good Enough History:** passive capture should make the map useful without obvious battery cost or requiring deliberate recording.

## Language

- **Coverage Map:** the main map showing visited coverage and remaining fog.
- **Fog of War:** the map overlay hiding places outside the user's Street-Level Coverage.
- **Street-Level Coverage:** visited streets, blocks, and similarly small areas.
- **Coverage Cell:** the H3 area used as the durable unit of visited coverage.
- **Coverage Compaction:** representing a complete set of fine Coverage Cells by their H3 parent without changing the logical Street-Level Coverage.
- **False Coverage:** a cell marked visited without credible evidence that the user passed through it or along a plausible nearby route. Small ambiguity around a credible route is not False Coverage.
- **Good Enough History:** useful passive history without requiring perfect capture.
- **Barely Noticeable Battery Use:** passive capture that does not create obvious battery anxiety in normal use.
- **Coverage Evidence:** retained information explaining why a cell is considered visited.
- **Observed Coverage Evidence:** Coverage Evidence created when an accepted Location Sample directly falls within a Coverage Cell.
- **Inferred Coverage Evidence:** Coverage Evidence created when Conservative Interpolation clears a Coverage Cell between accepted Location Samples without directly observing it.
- **Manual Route Claim:** the user's deliberate retrospective claim that they travelled approximately along a user-authored route, without asserting when. Each confirmation remains distinct so it can be withdrawn without removing other Coverage Evidence.
- **Manual Coverage Evidence:** Coverage Evidence created by a Manual Route Claim.
- **Accuracy-Gated Clearing:** rejecting evidence that is too inaccurate to clear fog safely.
- **Maximum Accepted Accuracy:** the largest reported horizontal-accuracy radius a Location Sample may have to create Coverage Evidence. It applies to both Passive Tracking and Recording Sessions.
- **Conservative Interpolation:** filling a short, plausible gap between accepted Location Samples along one shortest path of Coverage Cells.
- **Maximum Interpolation Gap:** the greatest H3 grid distance eligible for Conservative Interpolation. It applies to both Passive Tracking and Recording Sessions; a value of one disables interpolation.
- **Passive Gap:** a visited place left covered because passive evidence was insufficient.
- **Tracking Mode:** the current coverage-capture policy: Off, Passive, or Recording Session. Only one mode operates at a time; a Recording Session temporarily overrides Passive Tracking and stopping it returns to Passive when the durable opt-in remains enabled, otherwise Off.
- **Passive Tracking:** durable, best-effort opt-in capture that remains enabled until explicitly disabled, does not require starting a Recording Session, and may leave Passive Gaps when Android limits background delivery.
- **Passive Capture Window:** a self-terminating period that may collect multiple independently accuracy-gated fixes after a movement signal or conservative fallback while Passive Tracking is enabled. It ends at a time limit, a delivered-fix limit, or an earlier stationary signal; rejected fixes consume the delivery limit.
- **Opportunistic Fix:** a location fix derived for another system client and received by Kartoffel without causing additional location work. While Tracking Mode is Passive, an accurate Opportunistic Fix may clear its own Coverage Cell but does not open or extend a Passive Capture Window or justify automatic interpolation.
- **Recording Session:** deliberate, higher-fidelity capture for a route the user cares about.
- **Activity Mode:** the most recently recognized movement state that can be credibly aligned with when a location fix was captured. `Unknown` covers unavailable, absent, stale, or time-unalignable recognition, including fixes retained from before Activity Mode was recorded.
- **Requested Location Interval:** the location-update cadence Kartoffel asks Android to provide. It describes tracking policy, not the cadence Android actually delivers.
- **Tracking Diagnostics:** a secondary tuning view for inspecting what tracking is doing and why coverage was or was not cleared. During the MVP it is an owner/developer instrument, not a polished end-user explanation.
- **Diagnostic Sample Cell:** the H3 cell containing a retained Location Sample's reported coordinate, whether accepted or rejected. It does not itself represent Street-Level Coverage or the sample's accuracy radius; Tracking Inspection may also render Inferred Coverage Evidence in Coverage Cells containing no retained sample.
- **Evidence Compression:** reducing retained samples while preserving coverage and useful session history.
- **Evidence Retention:** how long evidence is kept before deletion or compression.

Prefer these terms in code, tests, issues, and UI copy. For simple screen-local UI text, inline strings are acceptable during MVP.
