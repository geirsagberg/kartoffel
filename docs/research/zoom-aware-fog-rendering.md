# Zoom-Aware Fog Rendering

Date: 2026-07-17

Status: exploratory plan; intentionally outside the current implementation package.

## Goal

Make Fog of War communicate evidence at different scales without changing the logical Coverage Set:

- confirmed Street-Level Coverage is strongest when viewed close up;
- confirmed coverage reveals less strongly when zoomed out;
- coarse passive Coverage Hints remain visibly weaker than confirmed coverage;
- adjacent cells read as one explored area rather than a collection of hexagons;
- transitions between coarse and fine evidence do not expose distracting large-versus-small hex boundaries.

The stored H3 cells remain evidence and coverage truth. Joining, smoothing, opacity, and zoom behavior belong to a disposable visual mask.

## Separate the two visual dimensions

Do not encode both ideas as a single “percentage cleared” value:

1. **Spatial precision** chooses the H3 resolution represented by the evidence.
2. **Reveal strength** controls how much fog remains over that evidence at a particular zoom.

The renderer can derive a final reveal value from evidence strength and a zoom curve. For example, confirmed coverage can approach full clearing at street zoom while retaining some fog at city zoom; a passive hint always remains weaker. Exact zoom thresholds and opacity values should be tuned visually rather than treated as domain facts.

## Feasibility of joining adjacent cells

Joining is directly supported by H3. [`cellsToMultiPolygon`](https://h3geo.org/docs/api/regions/) returns polygon outlines for a set of cells, dissolving internal edges and retaining outer rings and holes. Kartoffel's bundled H3 Android AAR already exposes this API.

There is an important constraint: H3 expects all input cells to have the same resolution and no duplicates; behavior for mixed resolutions is undefined. Therefore:

1. Select a render resolution appropriate for the zoom.
2. Expand compacted confirmed cells and coarse hints to that common resolution for the visible region.
3. Resolve overlaps by taking the strongest reveal value.
4. Group cells by reveal tier and call `cellsToMultiPolygon` only on same-resolution groups.

H3 hierarchy provides exact logical containment but only approximate geometric containment across resolutions ([H3 overview](https://h3geo.org/docs/)). Rendering a compacted parent boundary directly is therefore not equivalent to rendering the union of its resolution-11 descendants. Normalizing to a common render resolution avoids that error and also removes the large-versus-small hex mismatch.

Polygon joining alone removes internal borders, but the outside boundary still follows the H3 grid. That is already likely to be a material visual improvement.

## Bounded smoothing options

### Stage 1: joined polygon with antialiasing

Render the joined multipolygon into the existing tile bitmap with normal antialiasing. This removes internal hex edges without introducing a geometry dependency or expanding coverage beyond the union.

This should be the first prototype.

### Stage 2: feather the alpha-mask edge

Render reveal strength into a grayscale/alpha mask, then apply a small edge feather. Android's [`BlurMaskFilter`](https://developer.android.com/reference/android/graphics/BlurMaskFilter) explicitly blurs the edge of an alpha mask and supports inside, outside, and straddling styles.

Use a small screen-space radius. This softens the honeycomb silhouette but intentionally creates a narrow partially revealed band outside the exact cells. The band is visual only and must never become stored Coverage.

### Stage 3: geometric rounding only if needed

If feathering is insufficient, a geometry library such as JTS can union polygons and apply controlled positive/negative buffers. JTS defines buffering as geometric dilation/erosion and supports rounded joins ([`BufferOp`](https://locationtech.github.io/jts/javadoc/org/locationtech/jts/operation/buffer/BufferOp.html)); [`UnaryUnionOp`](https://locationtech.github.io/jts/javadoc/org/locationtech/jts/operation/union/UnaryUnionOp.html) merges polygon collections.

An outward buffer followed by an equal inward buffer can round corners and close tiny notches, but it may also join nearby areas or remove narrow fog gaps. Any radius needs a strict visual displacement budget. Adding JTS also increases APK size and CPU work, so this is not the default recommendation.

Topology-preserving simplification can reduce outline complexity while retaining shells and holes ([`TopologyPreservingSimplifier`](https://locationtech.github.io/jts/javadoc/org/locationtech/jts/simplify/TopologyPreservingSimplifier.html)), but simplification is not smoothing by itself.

### Later alternative: scalar-field contours

A more organic appearance could rasterize all confirmed coverage and hints into a continuous reveal field, lightly filter it, and extract or directly draw a contour. This naturally blends different cell sizes and strengths but is more complex to make deterministic and seam-free. Defer it unless joined polygons plus feathering are visibly inadequate.

## Proposed tile pipeline

For each requested `(x, y, zoom)` tile:

1. Expand the query bounds by a gutter large enough for antialiasing and smoothing.
2. Load confirmed coverage and passive hints intersecting the padded bounds.
3. Normalize relevant H3 cells to one render resolution for that zoom.
4. Compute each cell's zoom-adjusted reveal strength; confirmed coverage wins over hints.
5. Group equal or quantized reveal strengths and join adjacent cells with `cellsToMultiPolygon`.
6. Rasterize the joined polygons into a padded alpha mask.
7. Apply the bounded edge treatment.
8. Crop the center 256×256 tile, encode it, and cache by tile coordinate, coverage generation, and visual-style version.

The gutter is essential: filtering a tile in isolation creates visible seams where its neighbor lacks pixels from the other side. Smoothing parameters must be deterministic in world/screen coordinates at each zoom, and the padded query must include shapes just outside the tile.

## Suggested zoom behavior

Use a continuous curve rather than abrupt zoom bands, but design around these intents:

| View | Confirmed coverage | Passive hints | Shape treatment |
| --- | --- | --- | --- |
| Street | Strong or fully clear | Subtle | Exact/common-resolution outline, minimal feather |
| Neighborhood | Moderately strong | Faint | Joined outline with mild feather |
| City/region | Weaker | Very faint or hidden | Coarser render resolution and stronger generalization |

This preserves Fog of War as a meaningful large-scale layer instead of letting dense visited areas become enormous fully transparent holes when zoomed out.

## Guardrails

- Rendering never writes Coverage Cells or changes coverage totals.
- Confirmed coverage always renders at least as strongly as a hint.
- Mixed-resolution H3 cells are normalized before polygon joining.
- Coverage Compaction remains a storage concern, not a rendering shortcut.
- Edge feathering and rounding have a maximum displacement budget.
- Tiny gaps are closed only by an explicit visual policy, never implicitly promoted to Coverage.
- Repeated passive samples must not strengthen hints merely because the device remained stationary.

## Verification plan

Create golden tile tests for:

- one isolated resolution-11 cell;
- a chain and a solid cluster of adjacent cells;
- holes surrounded by coverage;
- compacted parents rendered identically to their resolution-11 descendants;
- overlapping confirmed coverage and coarse hints;
- large and small evidence cells meeting at a boundary;
- the same geometry on both sides of a tile seam;
- the same location across representative zoom levels;
- antimeridian and high-latitude tiles.

Measure tile latency, allocations, PNG size, cache hit rate, and visible seam count with representative 1k/10k/50k-cell histories.

## Recommendation

Prototype this in three independent switches, outside the current map package until the visual direction is validated:

1. zoom-dependent reveal strength;
2. common-resolution `cellsToMultiPolygon` joining;
3. a small padded alpha-mask feather.

That combination is feasible in the current Google Maps tile-overlay architecture and should address most visible hex-grid artifacts without changing engines or introducing JTS. Only explore geometric buffering or scalar-field contours if screenshots and device tests show the simpler pipeline is insufficient.
