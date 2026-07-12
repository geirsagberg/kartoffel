package net.sagberg.kartoffel.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverageMapStateTest {
    @Test
    fun foregroundLocationIsRequestedUntilAnAcceptedFixClearsCoverage() {
        assertFalse(
            shouldRequestForegroundLocation(
                hasLocationPermission = false,
                acceptedForegroundFix = false,
            )
        )
        assertTrue(
            shouldRequestForegroundLocation(
                hasLocationPermission = true,
                acceptedForegroundFix = false,
            )
        )
        assertFalse(
            shouldRequestForegroundLocation(
                hasLocationPermission = true,
                acceptedForegroundFix = true,
            )
        )
    }

    @Test
    fun firstLocationFixProducesTheAutomaticCenteringRequest() {
        val fix = MapCoordinate(latitude = 59.9139, longitude = 10.7522)

        val request = firstFixCameraRequest(
            firstFix = fix,
            centeredOnFirstFix = false,
        )

        assertEquals(fix, request?.target)
        assertEquals(FIRST_FIX_ZOOM, request?.zoom)
    }

    @Test
    fun firstLocationFixDoesNotCenterMoreThanOnce() {
        val request = firstFixCameraRequest(
            firstFix = MapCoordinate(latitude = 59.9139, longitude = 10.7522),
            centeredOnFirstFix = true,
        )

        assertNull(request)
    }
}
