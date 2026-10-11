package net.sagberg.kartoffel.map

import net.sagberg.kartoffel.coverage.CoverageCellId
import net.sagberg.kartoffel.coverage.GeoCoordinate

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
    val editable: Boolean = active,
)
