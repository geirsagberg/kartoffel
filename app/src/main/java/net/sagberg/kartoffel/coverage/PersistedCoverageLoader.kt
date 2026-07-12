package net.sagberg.kartoffel.coverage

import net.sagberg.kartoffel.storage.CoverageCellDao

internal class PersistedCoverageLoader(
    private val coverageCellDao: CoverageCellDao,
    private val coverageCells: H3CoverageCells = H3CoverageCells(),
) {
    suspend fun load(revision: Int): CoverageSnapshot =
        CoverageSnapshot(
            revision = revision,
            cells = coverageCellDao.all().map { entity ->
                val cell = CoverageCellId(entity.cellId)
                CoverageCellShape(
                    id = entity.cellId.toString(),
                    boundary = coverageCells.boundaryOf(cell),
                )
            },
        )
}
