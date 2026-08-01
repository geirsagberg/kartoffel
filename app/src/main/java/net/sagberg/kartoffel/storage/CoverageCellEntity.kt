package net.sagberg.kartoffel.storage

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "coverage_cells")
internal data class CoverageCellEntity(
    @PrimaryKey
    @ColumnInfo(name = "cell_id")
    val cellId: Long,
    @ColumnInfo(name = "first_seen_at_ms")
    val firstSeenAtMillis: Long,
    @ColumnInfo(name = "last_seen_at_ms")
    val lastSeenAtMillis: Long,
    @ColumnInfo(name = "evidence_mask")
    val evidenceMask: Int,
) {
    init {
        require(firstSeenAtMillis <= lastSeenAtMillis)
        require(evidenceMask > 0)
    }
}

internal enum class CoverageEvidenceSource(
    val persistedName: String,
    val observedBit: Int,
    val inferredBit: Int,
) {
    PASSIVE_TRACKING("passive_tracking", observedBit = 1, inferredBit = 4),
    RECORDING_SESSION("recording_session", observedBit = 2, inferredBit = 8),
}

internal fun evidenceMaskOf(vararg sources: CoverageEvidenceSource): Int =
    sources.fold(0) { mask, source -> mask or source.observedBit }

internal fun inferredEvidenceMaskOf(vararg sources: CoverageEvidenceSource): Int =
    sources.fold(0) { mask, source -> mask or source.inferredBit }
