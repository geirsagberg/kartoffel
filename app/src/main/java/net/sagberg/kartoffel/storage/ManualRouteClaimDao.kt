package net.sagberg.kartoffel.storage

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
internal interface ManualRouteClaimDao {
    @Insert
    suspend fun insertClaim(claim: ManualRouteClaimEntity): Long

    @Insert
    suspend fun insertCells(cells: List<ManualRouteClaimCellEntity>)

    @Transaction
    suspend fun create(createdAtMillis: Long, cellIds: Set<Long>): Long {
        require(cellIds.isNotEmpty())
        val claimId = insertClaim(ManualRouteClaimEntity(createdAtMillis = createdAtMillis))
        insertCells(cellIds.sorted().map { ManualRouteClaimCellEntity(claimId, it) })
        return claimId
    }

    @Query("DELETE FROM manual_route_claims WHERE id = :claimId")
    suspend fun withdraw(claimId: Long)

    @Query("SELECT id, created_at_ms FROM manual_route_claims ORDER BY created_at_ms DESC, id DESC")
    suspend fun allClaims(): List<ManualRouteClaimSummary>

    @Query("SELECT cell_id FROM manual_route_claim_cells WHERE claim_id = :claimId ORDER BY cell_id")
    suspend fun cellsForClaim(claimId: Long): List<Long>

    @Query("SELECT DISTINCT cell_id FROM manual_route_claim_cells ORDER BY cell_id")
    suspend fun allCellIds(): List<Long>

    @Query("SELECT DISTINCT cell_id FROM manual_route_claim_cells ORDER BY cell_id")
    fun observeAllCellIds(): Flow<List<Long>>
}
