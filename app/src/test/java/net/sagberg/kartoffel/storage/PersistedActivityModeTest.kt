package net.sagberg.kartoffel.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class PersistedActivityModeTest {
    @Test
    fun persistedNamesRemainIndependentOfDisplayLabels() {
        assertEquals(
            listOf("still", "walking", "running", "cycling", "in_vehicle", "unknown"),
            PersistedActivityMode.entries.map { it.persistedName },
        )
    }

    @Test
    fun unrecognizedStoredValuesAreReadAsUnknown() {
        assertEquals(
            PersistedActivityMode.UNKNOWN,
            PersistedActivityMode.fromPersistedName("future_mode"),
        )
    }
}
