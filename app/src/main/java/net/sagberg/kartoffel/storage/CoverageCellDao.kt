package net.sagberg.kartoffel.storage

import androidx.room3.Dao
import androidx.room3.Query

@Dao
internal interface CoverageCellDao {
    @Query("SELECT * FROM coverage_cells ORDER BY cell_id")
    suspend fun all(): List<CoverageCellEntity>

    @Query(
        """
        INSERT INTO coverage_cells(cell_id, first_seen_at_ms, last_seen_at_ms, evidence_mask)
        VALUES (:cellId, :firstSeenAtMillis, :lastSeenAtMillis, :evidenceMask)
        ON CONFLICT(cell_id) DO UPDATE SET
            first_seen_at_ms = MIN(first_seen_at_ms, excluded.first_seen_at_ms),
            last_seen_at_ms = MAX(last_seen_at_ms, excluded.last_seen_at_ms),
            evidence_mask = evidence_mask | excluded.evidence_mask
        """,
    )
    suspend fun upsert(
        cellId: Long,
        firstSeenAtMillis: Long,
        lastSeenAtMillis: Long,
        evidenceMask: Int,
    )

    @Query("SELECT * FROM coverage_cells WHERE cell_id = :cellId")
    suspend fun find(cellId: Long): CoverageCellEntity?

    @Query("SELECT * FROM coverage_cells WHERE cell_id IN (:cellIds)")
    suspend fun find(cellIds: Collection<Long>): List<CoverageCellEntity>
}
