package net.sagberg.kartoffel.map

import org.junit.Assert.assertEquals
import org.junit.Test

class FogTileFailureHandlingTest {
    @Test
    fun permanentDataCoordinateAndArgumentFailuresReturnNoTile() {
        listOf(
            IllegalArgumentException("invalid coordinate"),
            IndexOutOfBoundsException("malformed coverage boundary"),
            NoSuchElementException("missing coverage boundary"),
        ).forEach { failure ->
            assertEquals(FogTileFailureHandling.NoTile, fogTileFailureHandlingFor(failure))
        }
    }

    @Test
    fun transientRenderingAndRuntimeFailuresAllowRetry() {
        listOf(
            IllegalStateException("PNG compression failed"),
            RuntimeException("temporary renderer failure"),
        ).forEach { failure ->
            assertEquals(FogTileFailureHandling.Retry, fogTileFailureHandlingFor(failure))
        }
    }
}
