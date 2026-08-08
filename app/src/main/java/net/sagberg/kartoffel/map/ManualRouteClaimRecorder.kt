package net.sagberg.kartoffel.map

import net.sagberg.kartoffel.storage.ManualRouteClaimDao

internal class ManualRouteClaimRecorder(
    private val claims: ManualRouteClaimDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun confirm(preview: ManualRoutePreview): Long? {
        if (!preview.canConfirm) return null
        return claims.create(
            createdAtMillis = clock(),
            cellIds = preview.cells.map { it.value }.toSet(),
        )
    }
}
