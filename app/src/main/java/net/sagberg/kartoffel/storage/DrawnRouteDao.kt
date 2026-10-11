package net.sagberg.kartoffel.storage

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
internal interface DrawnRouteDao {
    @Insert
    suspend fun insertRoute(route: DrawnRouteEntity): Long

    @Insert
    suspend fun insertCells(cells: List<DrawnRouteCellEntity>)

    @Transaction
    suspend fun create(createdAtMillis: Long, cellIds: Set<Long>): Long {
        require(cellIds.isNotEmpty())
        val routeId = insertRoute(DrawnRouteEntity(createdAtMillis = createdAtMillis))
        insertCells(cellIds.sorted().map { DrawnRouteCellEntity(routeId, it) })
        return routeId
    }

    @Query("DELETE FROM drawn_routes WHERE id = :routeId")
    suspend fun remove(routeId: Long)

    @Query("SELECT id, created_at_ms FROM drawn_routes ORDER BY created_at_ms DESC, id DESC")
    suspend fun allRoutes(): List<DrawnRouteSummary>

    @Query("SELECT cell_id FROM drawn_route_cells WHERE route_id = :routeId ORDER BY cell_id")
    suspend fun cellsForRoute(routeId: Long): List<Long>

    @Query("SELECT DISTINCT cell_id FROM drawn_route_cells ORDER BY cell_id")
    suspend fun allCellIds(): List<Long>

    @Query("SELECT DISTINCT cell_id FROM drawn_route_cells ORDER BY cell_id")
    fun observeAllCellIds(): Flow<List<Long>>
}
