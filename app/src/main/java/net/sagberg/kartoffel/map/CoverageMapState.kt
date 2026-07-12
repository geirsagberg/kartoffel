package net.sagberg.kartoffel.map

internal const val FIRST_FIX_ZOOM = 15f
internal const val CURRENT_LOCATION_ZOOM = 16f

internal data class MapCoordinate(
    val latitude: Double,
    val longitude: Double,
)

internal data class MapCameraRequest(
    val target: MapCoordinate,
    val zoom: Float,
)

internal fun shouldRequestForegroundLocation(
    hasLocationPermission: Boolean,
    acceptedForegroundFix: Boolean,
): Boolean = hasLocationPermission && !acceptedForegroundFix

internal fun firstFixCameraRequest(
    firstFix: MapCoordinate?,
    centeredOnFirstFix: Boolean,
): MapCameraRequest? {
    if (centeredOnFirstFix) return null

    return firstFix?.let { target ->
        MapCameraRequest(
            target = target,
            zoom = FIRST_FIX_ZOOM,
        )
    }
}
