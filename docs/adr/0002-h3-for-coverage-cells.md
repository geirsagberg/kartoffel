# Use H3 for Coverage Cells

Kartoffel will implement coverage cells directly with H3 for the MVP because its hierarchical area cells match the fog-of-war model and give compact permanent identifiers for visited places. This does not require a preemptive runtime fallback or generic spatial-index abstraction; if H3 proves unstable or unsuitable on Android 16, the code will be changed then, with S2 as the preferred replacement and a custom lat/lon grid intentionally avoided unless both established indexes fail.
