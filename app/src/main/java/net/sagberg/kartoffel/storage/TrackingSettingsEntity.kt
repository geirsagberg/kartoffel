package net.sagberg.kartoffel.storage

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "tracking_settings")
internal data class TrackingSettingsEntity(
    @PrimaryKey
    val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "passive_enabled")
    val passiveEnabled: Boolean,
    @ColumnInfo(name = "passive_period_started_at_ms")
    val passivePeriodStartedAtMillis: Long?,
) {
    init {
        require(id == SINGLETON_ID)
        require(passiveEnabled == (passivePeriodStartedAtMillis != null))
    }

    companion object {
        const val SINGLETON_ID = 1
    }
}

@Dao
internal interface TrackingSettingsDao {
    @Query("SELECT * FROM tracking_settings WHERE id = 1")
    fun observe(): Flow<TrackingSettingsEntity?>

    @Query("SELECT * FROM tracking_settings WHERE id = 1")
    suspend fun current(): TrackingSettingsEntity?

    @Upsert
    suspend fun upsert(settings: TrackingSettingsEntity)
}

internal data class PassiveTrackingPreference(
    val enabled: Boolean,
    val passivePeriodStartedAtMillis: Long?,
) {
    companion object {
        val Disabled = PassiveTrackingPreference(
            enabled = false,
            passivePeriodStartedAtMillis = null,
        )
    }
}

internal class PassiveTrackingPreferences(
    private val dao: TrackingSettingsDao,
) {
    fun observe(): Flow<PassiveTrackingPreference> = dao.observe().map { it.toPreference() }

    suspend fun current(): PassiveTrackingPreference = dao.current().toPreference()

    suspend fun enable(passivePeriodStartedAtMillis: Long) {
        require(passivePeriodStartedAtMillis >= 0)
        dao.upsert(
            TrackingSettingsEntity(
                passiveEnabled = true,
                passivePeriodStartedAtMillis = passivePeriodStartedAtMillis,
            ),
        )
    }

    suspend fun disable() {
        dao.upsert(
            TrackingSettingsEntity(
                passiveEnabled = false,
                passivePeriodStartedAtMillis = null,
            ),
        )
    }
}

private fun TrackingSettingsEntity?.toPreference(): PassiveTrackingPreference =
    PassiveTrackingPreference(
        enabled = this?.passiveEnabled ?: false,
        passivePeriodStartedAtMillis = this?.passivePeriodStartedAtMillis,
    )
