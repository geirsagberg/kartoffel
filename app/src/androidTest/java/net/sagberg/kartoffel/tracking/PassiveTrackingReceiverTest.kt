package net.sagberg.kartoffel.tracking

import android.content.Intent
import android.location.Location
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.common.internal.safeparcel.SafeParcelableSerializer
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionEvent
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.LocationResult
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PassiveTrackingReceiverTest {
    @Test
    fun extractsActivityTransitionWithElapsedCaptureTime() {
        val intent = Intent()
        SafeParcelableSerializer.serializeToIntentExtra(
            ActivityTransitionResult(
                listOf(
                    ActivityTransitionEvent(
                        DetectedActivity.WALKING,
                        ActivityTransition.ACTIVITY_TRANSITION_ENTER,
                        2_500_000_000,
                    ),
                ),
            ),
            intent,
            "com.google.android.location.internal.EXTRA_ACTIVITY_TRANSITION_RESULT",
        )

        assertEquals(
            listOf(PassiveActivityObservation(RecordingActivity.WALKING, 2_500)),
            intent.passiveActivityObservations(),
        )
    }

    @Test
    fun extractsLocationDeliveriesInElapsedTimeOrder() {
        val later = location(elapsedRealtimeNanos = 3_000_000_000, latitude = 59.92)
        val earlier = location(elapsedRealtimeNanos = 2_000_000_000, latitude = 59.91)
        val intent = Intent().putExtra(
            "com.google.android.gms.location.EXTRA_LOCATION_RESULT",
            LocationResult.create(listOf(later, earlier)),
        )

        val deliveries = intent.passiveLocationDeliveries()

        assertEquals(listOf(2_000L, 3_000L), deliveries.map { it.capturedAtElapsedRealtimeMillis })
        assertEquals(listOf(59.91, 59.92), deliveries.map { it.fix.coordinate.latitude })
        assertEquals(listOf(8.0, 8.0), deliveries.map { it.fix.accuracyMeters })
    }

    private fun location(elapsedRealtimeNanos: Long, latitude: Double) =
        Location("test").apply {
            this.latitude = latitude
            longitude = 10.75
            time = 1_000
            accuracy = 8f
            this.elapsedRealtimeNanos = elapsedRealtimeNanos
        }
}
