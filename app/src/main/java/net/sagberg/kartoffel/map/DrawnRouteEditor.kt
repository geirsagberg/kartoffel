package net.sagberg.kartoffel.map

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.sagberg.kartoffel.coverage.CoverageCellId
import net.sagberg.kartoffel.coverage.GeoCoordinate
import net.sagberg.kartoffel.coverage.H3CoverageCells
import net.sagberg.kartoffel.storage.DrawnRouteDao

internal enum class DrawnRoutePhase { Inactive, Editing, Saving }

internal data class DrawnRouteEditingState(
    val phase: DrawnRoutePhase = DrawnRoutePhase.Inactive,
    val preview: DrawnRoutePreview = DrawnRoutePreview(),
) {
    val active: Boolean get() = phase != DrawnRoutePhase.Inactive
    val saving: Boolean get() = phase == DrawnRoutePhase.Saving
}

/** Owns one unconfirmed Drawn Route. Called from the Map's main-thread scope. */
internal class DrawnRouteEditor(
    private val pathBetween: (GeoCoordinate, GeoCoordinate) -> List<CoverageCellId>,
    private val saveRoute: suspend (Set<Long>) -> Long,
) {
    constructor(
        routes: DrawnRouteDao,
        h3: H3CoverageCells = H3CoverageCells(),
        clock: () -> Long = System::currentTimeMillis,
    ) : this(h3::pathBetween, { cells -> routes.create(clock(), cells) })

    private val editingState = MutableStateFlow(DrawnRouteEditingState())
    val state = editingState.asStateFlow()

    fun enter() {
        if (editingState.value.phase == DrawnRoutePhase.Inactive) {
            editingState.value = DrawnRouteEditingState(phase = DrawnRoutePhase.Editing)
        }
    }

    fun add(waypoint: GeoCoordinate) = edit { it + waypoint }

    fun move(index: Int, waypoint: GeoCoordinate) = edit { waypoints ->
        waypoints.toMutableList().apply { this[index] = waypoint }
    }

    fun undo() = edit { it.dropLast(1) }

    fun cancel() {
        if (editingState.value.phase == DrawnRoutePhase.Editing) {
            editingState.value = DrawnRouteEditingState()
        }
    }

    /** Null means there is no confirmable route, or a save is already running. */
    suspend fun confirm(): Result<Long>? {
        val draft = editingState.value
        if (draft.phase != DrawnRoutePhase.Editing || !draft.preview.canConfirm) return null
        editingState.value = draft.copy(phase = DrawnRoutePhase.Saving)
        return try {
            val routeId = saveRoute(draft.preview.cells.map { it.value }.toSet())
            editingState.value = DrawnRouteEditingState()
            Result.success(routeId)
        } catch (cancelled: CancellationException) {
            editingState.value = draft
            throw cancelled
        } catch (failure: Exception) {
            editingState.value = draft
            Result.failure(failure)
        }
    }

    private fun edit(change: (List<GeoCoordinate>) -> List<GeoCoordinate>) {
        val draft = editingState.value
        if (draft.phase != DrawnRoutePhase.Editing) return
        editingState.value = draft.copy(preview = previewOf(change(draft.preview.waypoints)))
    }

    private fun previewOf(waypoints: List<GeoCoordinate>): DrawnRoutePreview {
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
