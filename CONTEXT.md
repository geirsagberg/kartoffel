# Kartoffel

Kartoffel is a local-first Android app that remembers where its user has been and reveals that history on a **Map**.

## Product principles

- **Minimal by Default:** keep the main experience focused; put tuning and detail in secondary surfaces.
- **Local-First:** work without an account and keep location history under device-local control.
- **Clearly False Coverage is worse than a Gap:** short, locally plausible ambiguity may clear one credible route; implausible jumps should leave fog in place.
- **Good Enough History:** automatic capture should make the map useful without obvious battery cost or requiring deliberate recording.

## Language

- **Map:** the main map showing visited coverage and remaining Fog.
- **Fog:** the map overlay hiding places outside the user's Street-Level Coverage.
- **Coverage Outline:** the line drawn along the edge of visited coverage, over the Fog. Its style is a device-local appearance choice.
- **Street-Level Coverage:** visited streets, blocks, and similarly small areas.
- **Coverage Cell:** the H3 area used as the durable unit of visited coverage.
- **Coverage Compaction:** representing a complete set of fine Coverage Cells by their H3 parent without changing the logical Street-Level Coverage.
- **False Coverage:** a cell marked visited without credible evidence that the user passed through it or along a plausible nearby route. Small ambiguity around a credible route is not False Coverage.
- **Coverage Evidence:** retained information explaining why a cell is considered visited.
- **Observed Coverage Evidence:** Coverage Evidence created when an accepted Location Sample directly falls within a Coverage Cell.
- **Inferred Coverage Evidence:** Coverage Evidence created when Conservative Interpolation clears a Coverage Cell between accepted Location Samples without directly observing it.
- **Drawn Route:** a route the user draws to say they travelled roughly along it, by any means, without saying when. Each Drawn Route stays separate so it can be removed without removing other Coverage Evidence.
- **Drawn Coverage Evidence:** Coverage Evidence created by a Drawn Route.
- **Accuracy-Gated Clearing:** rejecting evidence that is too inaccurate to clear fog safely.
- **Maximum Accepted Accuracy:** the largest reported horizontal-accuracy radius a Location Sample may have to create Coverage Evidence. It applies to both Automatic Tracking and Recordings.
- **Conservative Interpolation:** filling a short, plausible gap between accepted Location Samples along one shortest path of Coverage Cells.
- **Maximum Interpolation Distance:** the greatest H3 grid distance eligible for Conservative Interpolation. It applies to both Automatic Tracking and Recordings; a value of one disables interpolation.
- **Gap:** a visited place left fogged because Automatic Tracking did not capture enough evidence.
- **Tracking Mode:** the current coverage-capture policy: Off, Automatic, or Recording. Only one mode operates at a time; a Recording temporarily overrides Automatic Tracking and stopping it returns to Automatic when the durable opt-in remains enabled, otherwise Off.
- **Automatic Tracking:** durable, best-effort opt-in capture that remains enabled until explicitly disabled, does not require starting a Recording, and may leave Gaps when Android limits background delivery.
- **Capture Window:** a self-terminating period that may collect multiple independently accuracy-gated fixes after a movement signal or conservative fallback while Automatic Tracking is enabled. It ends at a time limit, a delivered-fix limit, or an earlier stationary signal; rejected fixes consume the delivery limit.
- **Opportunistic Fix:** a location fix derived for another system client and received by Kartoffel without causing additional location work. While Tracking Mode is Automatic, an accurate Opportunistic Fix may clear its own Coverage Cell and participate in Conservative Interpolation under the same settings and two-minute limit as other automatic fixes. It does not open or extend a Capture Window.
- **Recording:** deliberate, higher-fidelity capture for a route the user cares about.
- **Location Sample:** a location fix Kartoffel stores, whether accepted or rejected.
- **Activity Mode:** the most recently recognized movement state that can be credibly aligned with when a location fix was captured. `Unknown` covers unavailable, absent, stale, or time-unalignable recognition, including fixes retained from before Activity Mode was recorded.
- **Requested Location Interval:** the location-update cadence Kartoffel asks Android to provide. It describes tracking policy, not the cadence Android actually delivers.
- **Tracking Diagnostics:** a secondary tuning view for inspecting what tracking is doing and why coverage was or was not cleared. During the MVP it is an owner/developer instrument, not a polished end-user explanation.
- **Tracking Inspection:** reviewing stored Location Samples and Coverage Evidence on the Map.
- **Diagnostic Sample Cell:** the H3 cell containing a retained Location Sample's reported coordinate, whether accepted or rejected. It does not itself represent Street-Level Coverage or the sample's accuracy radius; Tracking Inspection may also render Inferred Coverage Evidence in Coverage Cells containing no retained sample.

Prefer plain words: a term good enough for the UI is good enough here. Use these terms in code, tests, issues, and UI copy. For simple screen-local UI text, inline strings are acceptable during MVP.
