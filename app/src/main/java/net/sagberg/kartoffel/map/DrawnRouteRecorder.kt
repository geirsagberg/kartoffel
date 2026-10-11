package net.sagberg.kartoffel.map

import net.sagberg.kartoffel.storage.DrawnRouteDao

internal class DrawnRouteRecorder(
    private val routes: DrawnRouteDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun confirm(preview: DrawnRoutePreview): Long? {
        if (!preview.canConfirm) return null
        return routes.create(
            createdAtMillis = clock(),
            cellIds = preview.cells.map { it.value }.toSet(),
        )
    }
}
