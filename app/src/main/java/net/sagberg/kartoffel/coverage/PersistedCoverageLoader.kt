package net.sagberg.kartoffel.coverage

import net.sagberg.kartoffel.storage.CoverageCellDao
import net.sagberg.kartoffel.storage.CoverageCellEntity
import net.sagberg.kartoffel.storage.DrawnRouteDao

internal class PersistedCoverageLoader(
    private val coverageCellDao: CoverageCellDao,
    private val drawnRouteDao: DrawnRouteDao? = null,
    private val coverageCells: H3CoverageCells = H3CoverageCells(),
) {
    suspend fun load(): CoverageSnapshot = load(
        coverageCellDao.all(),
        drawnRouteDao?.allCellIds().orEmpty(),
    )

    fun load(
        entities: List<CoverageCellEntity>,
        manualCellIds: List<Long> = emptyList(),
    ): CoverageSnapshot =
        CoverageSnapshot(
            cells = (entities.map(CoverageCellEntity::cellId) + manualCellIds)
                .distinct()
                .sorted()
                .map { cellId -> coverageCells.shapeOf(CoverageCellId(cellId)) },
        )
}
