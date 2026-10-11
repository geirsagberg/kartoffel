package net.sagberg.kartoffel.storage

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.sagberg.kartoffel.settings.CoverageSettings
import net.sagberg.kartoffel.settings.CoverageSettingsRepository

@Entity(tableName = "coverage_settings")
internal data class CoverageSettingsEntity(
    @PrimaryKey
    val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "maximum_accepted_accuracy_meters")
    val maximumAcceptedAccuracyMeters: Int,
    @ColumnInfo(name = "maximum_interpolation_distance_steps")
    val maximumInterpolationDistanceSteps: Int,
) {
    init {
        require(id == SINGLETON_ID)
    }

    companion object {
        const val SINGLETON_ID = 1
    }
}

@Dao
internal interface CoverageSettingsDao {
    @Query("SELECT * FROM coverage_settings WHERE id = 1")
    fun observe(): Flow<CoverageSettingsEntity?>

    @Query("SELECT * FROM coverage_settings WHERE id = 1")
    suspend fun current(): CoverageSettingsEntity?

    @Upsert
    suspend fun upsert(settings: CoverageSettingsEntity)

    @Query(
        "INSERT INTO coverage_settings " +
            "(id, maximum_accepted_accuracy_meters, maximum_interpolation_distance_steps) " +
            "VALUES (1, :value, :defaultInterpolationDistanceSteps) " +
            "ON CONFLICT(id) DO UPDATE SET " +
            "maximum_accepted_accuracy_meters = :value",
    )
    suspend fun setMaximumAcceptedAccuracyMeters(
        value: Int,
        defaultInterpolationDistanceSteps: Int,
    )

    @Query(
        "INSERT INTO coverage_settings " +
            "(id, maximum_accepted_accuracy_meters, maximum_interpolation_distance_steps) " +
            "VALUES (1, :defaultAcceptedAccuracyMeters, :value) " +
            "ON CONFLICT(id) DO UPDATE SET " +
            "maximum_interpolation_distance_steps = :value",
    )
    suspend fun setMaximumInterpolationDistanceSteps(
        value: Int,
        defaultAcceptedAccuracyMeters: Int,
    )
}

internal class RoomCoverageSettings(
    private val dao: CoverageSettingsDao,
) : CoverageSettingsRepository {
    private val updateMutex = Mutex()

    override fun observe(): Flow<CoverageSettings> = dao.observe().map { it.toSettings() }

    override suspend fun current(): CoverageSettings = updateMutex.withLock {
        dao.current().toSettings()
    }

    override suspend fun setMaximumAcceptedAccuracyMeters(value: Int) {
        CoverageSettings.Default.copy(maximumAcceptedAccuracyMeters = value)
        updateMutex.withLock {
            dao.setMaximumAcceptedAccuracyMeters(
                value = value,
                defaultInterpolationDistanceSteps = CoverageSettings.Default
                    .maximumInterpolationDistanceSteps,
            )
        }
    }

    override suspend fun setMaximumInterpolationDistanceSteps(value: Int) {
        CoverageSettings.Default.copy(maximumInterpolationDistanceSteps = value)
        updateMutex.withLock {
            dao.setMaximumInterpolationDistanceSteps(
                value = value,
                defaultAcceptedAccuracyMeters = CoverageSettings.Default
                    .maximumAcceptedAccuracyMeters,
            )
        }
    }

    override suspend fun reset() {
        updateMutex.withLock {
            dao.upsert(CoverageSettings.Default.toEntity())
        }
    }
}

private fun CoverageSettingsEntity?.toSettings(): CoverageSettings = this?.let {
    CoverageSettings(
        maximumAcceptedAccuracyMeters = maximumAcceptedAccuracyMeters,
        maximumInterpolationDistanceSteps = maximumInterpolationDistanceSteps,
    )
} ?: CoverageSettings.Default

private fun CoverageSettings.toEntity(): CoverageSettingsEntity = CoverageSettingsEntity(
    maximumAcceptedAccuracyMeters = maximumAcceptedAccuracyMeters,
    maximumInterpolationDistanceSteps = maximumInterpolationDistanceSteps,
)
