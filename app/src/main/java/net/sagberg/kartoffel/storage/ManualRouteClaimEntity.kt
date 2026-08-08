package net.sagberg.kartoffel.storage

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(tableName = "manual_route_claims")
internal data class ManualRouteClaimEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "created_at_ms")
    val createdAtMillis: Long,
)

@Entity(
    tableName = "manual_route_claim_cells",
    primaryKeys = ["claim_id", "cell_id"],
    foreignKeys = [
        ForeignKey(
            entity = ManualRouteClaimEntity::class,
            parentColumns = ["id"],
            childColumns = ["claim_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("claim_id"), Index("cell_id")],
)
internal data class ManualRouteClaimCellEntity(
    @ColumnInfo(name = "claim_id")
    val claimId: Long,
    @ColumnInfo(name = "cell_id")
    val cellId: Long,
)

internal data class ManualRouteClaimSummary(
    val id: Long,
    @ColumnInfo(name = "created_at_ms")
    val createdAtMillis: Long,
)
