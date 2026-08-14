package net.sagberg.kartoffel.map

import android.content.Context
import androidx.core.content.edit
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng

internal class MapCameraPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): CameraPosition? {
        if (!preferences.contains(KEY_LATITUDE) ||
            !preferences.contains(KEY_LONGITUDE) ||
            !preferences.contains(KEY_ZOOM)
        ) {
            return null
        }

        val latitude = preferences.getString(KEY_LATITUDE, null)?.toDoubleOrNull()
            ?: return null
        val longitude = preferences.getString(KEY_LONGITUDE, null)?.toDoubleOrNull()
            ?: return null
        val zoom = preferences.getFloat(KEY_ZOOM, Float.NaN)
        val bearing = preferences.getFloat(KEY_BEARING, 0f)
        val tilt = preferences.getFloat(KEY_TILT, 0f)

        if (latitude !in -90.0..90.0 ||
            longitude !in -180.0..180.0 ||
            !zoom.isFinite() ||
            !bearing.isFinite() ||
            tilt !in 0f..90f
        ) {
            return null
        }

        return CameraPosition(
            LatLng(latitude, longitude),
            zoom,
            tilt,
            bearing,
        )
    }

    fun save(position: CameraPosition) {
        preferences.edit {
            putString(KEY_LATITUDE, position.target.latitude.toString())
            putString(KEY_LONGITUDE, position.target.longitude.toString())
            putFloat(KEY_ZOOM, position.zoom)
            putFloat(KEY_BEARING, position.bearing)
            putFloat(KEY_TILT, position.tilt)
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "map_camera"
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
        const val KEY_ZOOM = "zoom"
        const val KEY_BEARING = "bearing"
        const val KEY_TILT = "tilt"
    }
}
