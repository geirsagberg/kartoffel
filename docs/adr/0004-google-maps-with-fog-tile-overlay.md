# Use Google Maps with a Fog Tile Overlay

Kartoffel will use Google Maps SDK for Android for the MVP map and render fog of war as a custom tile overlay. This keeps the base map polished while making the fog layer owned by the app, and avoids making MapLibre or custom vector styling part of the MVP unless Google Maps blocks the fog overlay on styling, offline control, or performance.
