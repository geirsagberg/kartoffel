package net.sagberg.kartoffel.map

import net.sagberg.kartoffel.coverage.CoverageCellId
import net.sagberg.kartoffel.coverage.GeoCoordinate
import net.sagberg.kartoffel.coverage.H3CoverageCells

internal data class ManualRoutePreview(
    val waypoints: List<GeoCoordinate> = emptyList(),
    val cells: Set<CoverageCellId> = emptySet(),
    val failingSegmentIndex: Int? = null,
) {
    val canConfirm: Boolean get() = waypoints.size >= 2 && failingSegmentIndex == null
}

internal data class ManualRouteMapInteractions(
    val active: Boolean,
    val preview: ManualRoutePreview,
    val onMapClick: (GeoCoordinate) -> Unit,
    val onWaypointDragEnd: (Int, GeoCoordinate) -> Unit,
)

internal class ManualRouteDrawing(
    private val pathBetween: (GeoCoordinate, GeoCoordinate) -> List<CoverageCellId>,
) {
    constructor(h3: H3CoverageCells) : this(h3::pathBetween)

    constructor() : this(H3CoverageCells())

    fun add(preview: ManualRoutePreview, waypoint: GeoCoordinate): ManualRoutePreview =
        recompute(preview.waypoints + waypoint)

    fun move(
        preview: ManualRoutePreview,
        index: Int,
        waypoint: GeoCoordinate,
    ): ManualRoutePreview = recompute(preview.waypoints.toMutableList().apply { this[index] = waypoint })

    fun undo(preview: ManualRoutePreview): ManualRoutePreview =
        recompute(preview.waypoints.dropLast(1))

    fun recompute(waypoints: List<GeoCoordinate>): ManualRoutePreview {
        if (waypoints.size < 2) return ManualRoutePreview(waypoints = waypoints)
        val cells = linkedSetOf<CoverageCellId>()
        waypoints.zipWithNext().forEachIndexed { index, (start, destination) ->
            val segment = runCatching { pathBetween(start, destination) }
                .getOrElse {
                    return ManualRoutePreview(
                        waypoints = waypoints,
                        cells = cells,
                        failingSegmentIndex = index,
                    )
                }
            cells += segment
        }
        return ManualRoutePreview(waypoints = waypoints, cells = cells)
    }
}
