package net.sagberg.kartoffel.coverage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GeoCoordinateTest {
    @Test
    fun acceptsLatitudeAndLongitudeEdgeValues() {
        val coordinates = listOf(
            GeoCoordinate(latitude = -90.0, longitude = -180.0),
            GeoCoordinate(latitude = 90.0, longitude = 180.0),
            GeoCoordinate(latitude = 0.0, longitude = 0.0),
        )

        assertEquals(-90.0, coordinates[0].latitude, 0.0)
        assertEquals(180.0, coordinates[1].longitude, 0.0)
        assertEquals(0.0, coordinates[2].latitude, 0.0)
    }

    @Test
    fun rejectsLatitudesOutsideWorldRange() {
        listOf(
            -90.0001,
            90.0001,
            Double.NEGATIVE_INFINITY,
            Double.POSITIVE_INFINITY,
            Double.NaN,
        )
            .forEach { latitude ->
                val failure = assertThrows(IllegalArgumentException::class.java) {
                    GeoCoordinate(latitude = latitude, longitude = 0.0)
                }

                assertEquals(
                    "latitude must be between -90.0 and 90.0 degrees inclusive; was $latitude",
                    failure.message,
                )
            }
    }

    @Test
    fun rejectsLongitudesOutsideWorldRange() {
        listOf(
            -180.0001,
            180.0001,
            Double.NEGATIVE_INFINITY,
            Double.POSITIVE_INFINITY,
            Double.NaN,
        )
            .forEach { longitude ->
                val failure = assertThrows(IllegalArgumentException::class.java) {
                    GeoCoordinate(latitude = 0.0, longitude = longitude)
                }

                assertEquals(
                    "longitude must be between -180.0 and 180.0 degrees inclusive; was $longitude",
                    failure.message,
                )
            }
    }
}
