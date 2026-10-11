package net.sagberg.kartoffel.map

import net.sagberg.kartoffel.coverage.CoverageCellId
import net.sagberg.kartoffel.coverage.GeoCoordinate
import net.sagberg.kartoffel.coverage.H3CoverageCells

internal data class DrawnRoutePreview(
    val waypoints: List<GeoCoordinate> = emptyList(),
    val cells: Set<CoverageCellId> = emptySet(),
    val failingSegmentIndex: Int? = null,
) {
    val canConfirm: Boolean get() = waypoints.size >= 2 && failingSegmentIndex == null
}

internal data class DrawnRouteMapInteractions(
    val active: Boolean,
    val preview: DrawnRoutePreview,
    val onMapClick: (GeoCoordinate) -> Unit,
    val onWaypointDragEnd: (Int, GeoCoordinate) -> Unit,
)

internal class RouteDrawing(
    private val pathBetween: (GeoCoordinate, GeoCoordinate) -> List<CoverageCellId>,
) {
    constructor(h3: H3CoverageCells) : this(h3::pathBetween)

    constructor() : this(H3CoverageCells())

    fun add(preview: DrawnRoutePreview, waypoint: GeoCoordinate): DrawnRoutePreview =
        recompute(preview.waypoints + waypoint)

    fun move(
        preview: DrawnRoutePreview,
        index: Int,
        waypoint: GeoCoordinate,
    ): DrawnRoutePreview = recompute(preview.waypoints.toMutableList().apply { this[index] = waypoint })

    fun undo(preview: DrawnRoutePreview): DrawnRoutePreview =
        recompute(preview.waypoints.dropLast(1))

    fun recompute(waypoints: List<GeoCoordinate>): DrawnRoutePreview {
        if (waypoints.size < 2) return DrawnRoutePreview(waypoints = waypoints)
        val cells = linkedSetOf<CoverageCellId>()
        waypoints.zipWithNext().forEachIndexed { index, (start, destination) ->
            val segment = runCatching { pathBetween(start, destination) }
                .getOrElse {
                    return DrawnRoutePreview(
                        waypoints = waypoints,
                        cells = cells,
                        failingSegmentIndex = index,
                    )
                }
            cells += segment
        }
        return DrawnRoutePreview(waypoints = waypoints, cells = cells)
    }
}
