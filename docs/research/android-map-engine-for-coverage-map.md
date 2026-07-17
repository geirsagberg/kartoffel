# Android Map Engine for the Coverage Map

Date: 2026-07-17

## Question

Would MapLibre Native, Mapbox Maps SDK, or another Android map engine materially improve Kartoffel's dynamic Fog of War performance over Google Maps SDK for Android?

## Short answer

Possibly, but not enough to justify a migration for the reported blink alone.

MapLibre and Mapbox expose map data as renderer-native sources and layers. That is a better conceptual fit for frequently changing geographic data than Google's bitmap `TileOverlay`. However, Kartoffel's fog is an **inverse mask**—an opaque world with visited H3 cells cut out. Neither engine's documented Android API provides an obviously cheap incremental “subtract one hole” operation. Replacing the Google raster path with one large GeoJSON polygon could merely trade PNG work for repeated GeoJSON rebuild, tiling, and triangulation work.

The current recommendation is to retain Google Maps, add app-owned tile caching and affected-tile invalidation around the existing renderer, and reconsider the engine only if offline basemaps or full styling/data control become product requirements. Before migrating, benchmark a small MapLibre prototype with representative coverage histories.

## Performance-relevant comparison

| Concern | Google Maps SDK | MapLibre Native | Mapbox Maps SDK |
| --- | --- | --- | --- |
| Fog primitive | App-produced raster `TileOverlay` | GeoJSON/vector/raster style source and layer | GeoJSON/vector/raster style source and layer |
| Update granularity | `clearTileCache()` reloads **all tiles in the overlay**; there is no documented per-tile invalidation | Stable source can be replaced with `GeoJsonSource.setGeoJson()`; renderer documentation says cached tile sets are reused and only dirty viewport tiles are replaced | Stable runtime sources/layers; current API also exposes feature-oriented GeoJSON source updates |
| CPU/GPU work | Kartoffel draws H3 paths into Android bitmaps and PNG-encodes them before the SDK uploads/decodes them | Native renderer is GPU-accelerated; vector styling avoids Kartoffel's bitmap/PNG path | Native GPU renderer and runtime style layers; broadly the same architectural advantage |
| Large history risk | Spatially partitioned tiles naturally bound work per request, but Google's cache clear is coarse | A single large GeoJSON source or giant polygon-with-holes can become expensive; MapLibre itself warns that large client GeoJSON datasets are challenging | Better incremental APIs than MapLibre, but inverse-mask geometry and scale still require measurement |
| Offline basemap | No developer-managed offline-region API is documented for Maps SDK; the SDK downloads and may cache map tiles itself | Explicit offline regions and local styles/data; tile/style provider remains Kartoffel's responsibility | Explicit style packs and tile regions, with documented limits and lifecycle management |

Google's documented behavior is directly relevant to the blink: after provider data changes, [`clearTileCache()` causes all tiles on the overlay to reload](https://developers.google.com/maps/documentation/android-sdk/tileoverlay). Setting `fadeIn = false` prevents a fade animation but does not make regenerated tiles immediately available.

MapLibre can keep a source and layer stable while replacing its data with [`GeoJsonSource.setGeoJson()`](https://maplibre.org/maplibre-native/android/examples/geojson-guide/). Its renderer documentation says it [reuses cached tile sets and replaces only dirty viewport tiles](https://maplibre.org/maplibre-native/docs/book/design/android-map-rendering-data-flow.html). This is a better invalidation model in principle, but it is not proof of better performance for Kartoffel's mask.

The difficult part is representing the fog. Supplying all visited hexagons as ordinary transparent fills does not hide the unvisited map. An inverse mask needs a world/viewport polygon with holes, a partitioned vector/raster mask, or custom rendering. Rebuilding a growing polygon-with-holes for each new cell could invalidate and retriangulate substantial geometry. A tiled representation preserves the useful property that one new H3 cell affects only a small number of tiles at each zoom.

## Double-buffering and lower-cost improvements

Double-buffering two Google tile overlays could keep the old overlay visible while a new one warms, then swap them. The API does not document an overlay-wide “all visible tiles ready” signal, though, and compositing old and new fog simultaneously leaves newly visited holes covered. A timed swap would hide some latency but remain heuristic.

A more useful game/rendering technique is an app-owned backing cache:

1. Cache rendered PNG bytes by `(x, y, zoom, coverage generation)` or by a content signature.
2. When a new H3 cell is added, determine the tile keys it intersects at supported zooms and evict only those app-cache entries.
3. Call Google's required overlay-wide `clearTileCache()`.
4. Serve unchanged tiles immediately from the app cache; render only affected visible tiles.
5. Optionally pre-render the affected currently visible tiles before clearing Google's cache.

Google will still request all visible overlay tiles, but most requests become memory lookups. This retains the existing spatially bounded fog renderer and has much lower migration risk.

## Product and operational trade-offs

### Google Maps SDK

Google supplies the basemap, labels, styling, and Android map integration already used by Kartoffel. The current [native Maps SDK SKU is listed as unlimited](https://developers.google.com/maps/billing-and-pricing/pricing), although billing and an API key are required and other Google services have separate prices. The trade-off is limited renderer control and coarse custom-tile invalidation.

### MapLibre Native

MapLibre Native is [BSD-2-Clause, open source, and GPU-accelerated](https://github.com/maplibre/maplibre-native); Android is a [core platform](https://maplibre.org/maplibre-native/docs/book/platforms/index.html). It has no engine MAU charge, but it is an engine rather than a Google-equivalent basemap service. Kartoffel must choose or host styles, vector/raster tiles, fonts, and sprites and comply with each data provider's license and attribution. Its Android API has [offline-region management](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.offline/index.html), which is strategically valuable for a local-first app.

Migration is substantial: replace Google camera, location layer, map lifecycle, tile overlay, styling, and Compose integration; choose a tile provider; and re-test gestures and rendering across devices. The official MapLibre Compose project says [API stability is not guaranteed and supported features may have bugs](https://maplibre.org/maplibre-compose/), so the mature Android `MapView` plus a Compose wrapper may be the safer integration today.

### Mapbox Maps SDK

Mapbox offers the same general renderer-native source/layer and managed-offline advantages, with a polished commercial basemap ecosystem. Offline operation requires managing both [style packs and tile regions](https://docs.mapbox.com/android/maps/guides/offline/concepts/). The current Maps SDK is commercially licensed and [priced by monthly active users](https://www.mapbox.com/pricing) after its free tier; it also has attribution and telemetry/opt-out requirements documented in the [Android SDK guide](https://docs.mapbox.com/android/maps/guides/). It is a realistic choice if Kartoffel wants a managed vector-map platform, but not a compelling performance-only replacement for Google.

## Recommendation and decision gate

Do not migrate map engines solely to address the fog blink or occasional pan/zoom gaps.

First, measure and optimize the current tile path with app-owned caching and affected-tile pre-rendering. Migrate only if at least one broader requirement becomes important:

- predictable developer-managed offline areas;
- non-Google/self-hosted basemap data;
- extensive vector styling or map-feature interaction;
- measurements show the optimized Google tile path still misses the frame/latency target.

If that gate is reached, prototype **MapLibre Native first**, because it best matches Kartoffel's local-first direction and avoids engine MAU fees. Test at 1,000, 10,000, and 50,000 coverage cells on a representative low/mid-range phone, measuring initial load, one-cell update latency, panning frame time, zooming frame time, memory, and APK size. Compare two fog implementations: a viewport-bounded inverse GeoJSON mask and partitioned local fog tiles. A migration should follow evidence from that benchmark, not the expectation that GPU vector rendering is automatically faster.
