package net.sagberg.kartoffel.storage

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(tableName = "drawn_routes")
internal data class DrawnRouteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "created_at_ms")
    val createdAtMillis: Long,
)

@Entity(
    tableName = "drawn_route_cells",
    primaryKeys = ["route_id", "cell_id"],
    foreignKeys = [
        ForeignKey(
            entity = DrawnRouteEntity::class,
            parentColumns = ["id"],
            childColumns = ["route_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("route_id"), Index("cell_id")],
)
internal data class DrawnRouteCellEntity(
    @ColumnInfo(name = "route_id")
    val routeId: Long,
    @ColumnInfo(name = "cell_id")
    val cellId: Long,
)

internal data class DrawnRouteSummary(
    val id: Long,
    @ColumnInfo(name = "created_at_ms")
    val createdAtMillis: Long,
)
