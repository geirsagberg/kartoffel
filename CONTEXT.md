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
- **False Coverage:** a cell marked visited without credible evidence that the user passed nearby.
- **Good Enough History:** useful passive history without requiring perfect capture.
- **Barely Noticeable Battery Use:** passive capture that does not create obvious battery anxiety in normal use.
- **Coverage Evidence:** retained information explaining why a cell is considered visited.
- **Accuracy-Gated Clearing:** rejecting evidence that is too inaccurate to clear fog safely.
- **Conservative Interpolation:** filling short, plausible gaps between samples. When several equally short local routes are credible, all may be filled.
- **Passive Gap:** a visited place left covered because passive evidence was insufficient.
- **Passive Tracking:** opt-in capture without starting a Recording Session.
- **Recording Session:** deliberate, higher-fidelity capture for a route the user cares about.
- **Tracking Diagnostics:** a secondary view explaining capture, rejection, gaps, and clearing.
- **Evidence Compression:** reducing retained samples while preserving coverage and useful session history.
- **Evidence Retention:** how long evidence is kept before deletion or compression.

Prefer these terms in code, tests, issues, and UI copy. For simple screen-local UI text, inline strings are acceptable during MVP.
