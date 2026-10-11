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
    @ColumnInfo(name = "automatic_enabled")
    val automaticEnabled: Boolean,
    @ColumnInfo(name = "automatic_period_started_at_ms")
    val automaticPeriodStartedAtMillis: Long?,
) {
    init {
        require(id == SINGLETON_ID)
        require(automaticEnabled == (automaticPeriodStartedAtMillis != null))
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

internal data class AutomaticTrackingPreference(
    val enabled: Boolean,
    val automaticPeriodStartedAtMillis: Long?,
) {
    companion object {
        val Disabled = AutomaticTrackingPreference(
            enabled = false,
            automaticPeriodStartedAtMillis = null,
        )
    }
}

internal class AutomaticTrackingPreferences(
    private val dao: TrackingSettingsDao,
) {
    fun observe(): Flow<AutomaticTrackingPreference> = dao.observe().map { it.toPreference() }

    suspend fun current(): AutomaticTrackingPreference = dao.current().toPreference()

    suspend fun enable(automaticPeriodStartedAtMillis: Long) {
        require(automaticPeriodStartedAtMillis >= 0)
        dao.upsert(
            TrackingSettingsEntity(
                automaticEnabled = true,
                automaticPeriodStartedAtMillis = automaticPeriodStartedAtMillis,
            ),
        )
    }

    suspend fun disable() {
        dao.upsert(
            TrackingSettingsEntity(
                automaticEnabled = false,
                automaticPeriodStartedAtMillis = null,
            ),
        )
    }
}

private fun TrackingSettingsEntity?.toPreference(): AutomaticTrackingPreference =
    AutomaticTrackingPreference(
        enabled = this?.automaticEnabled ?: false,
        automaticPeriodStartedAtMillis = this?.automaticPeriodStartedAtMillis,
    )
