package net.sagberg.kartoffel.coverage

import net.sagberg.kartoffel.storage.CoverageCellDao
import net.sagberg.kartoffel.storage.CoverageCellEntity

internal class PersistedCoverageLoader(
    private val coverageCellDao: CoverageCellDao,
    private val coverageCells: H3CoverageCells = H3CoverageCells(),
) {
    suspend fun load(): CoverageSnapshot =
        load(coverageCellDao.all())

    fun load(entities: List<CoverageCellEntity>): CoverageSnapshot =
        CoverageSnapshot(
            cells = entities.map { entity ->
                val cell = CoverageCellId(entity.cellId)
                CoverageCellShape(
                    id = entity.cellId.toString(),
                    boundary = coverageCells.boundaryOf(cell),
                )
            },
        )
}
