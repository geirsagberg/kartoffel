package net.sagberg.kartoffel.coverage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundLocationDecisionTest {
    @Test
    fun accurateForegroundFixIsAccepted() {
        val decision = decideForegroundLocation(
            fix = fix(accuracyMeters = MAX_FOREGROUND_ACCURACY_METERS),
        )

        assertTrue(decision.accepted)
        assertNull(decision.rejectionReason)
    }

    @Test
    fun inaccurateForegroundFixIsRejectedWithoutClaimingCoverage() {
        val decision = decideForegroundLocation(
            fix = fix(accuracyMeters = MAX_FOREGROUND_ACCURACY_METERS + 0.1),
        )

        assertEquals(false, decision.accepted)
        assertEquals(FOREGROUND_ACCURACY_REJECTION, decision.rejectionReason)
    }

    private fun fix(accuracyMeters: Double) = ForegroundLocationFix(
        coordinate = GeoCoordinate(latitude = 59.9109, longitude = 10.7522),
        capturedAtMillis = 1_000,
        accuracyMeters = accuracyMeters,
    )
}
