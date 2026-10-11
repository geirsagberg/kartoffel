package net.sagberg.kartoffel.map

import android.graphics.Point
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.Projection
import com.google.android.gms.maps.model.LatLng
import net.sagberg.kartoffel.coverage.GeoCoordinate

/**
 * Handles Drawn Route taps and waypoint drags before the map view sees them.
 *
 * The map's own click listener waits for the double-tap timeout, and its marker drag waits for
 * a long press. Here a tap adds a waypoint as soon as the finger lifts, and touching a waypoint
 * drags it at once. Pans and pinches still reach the map. Consuming a down keeps the map view
 * out of that whole gesture.
 */
internal fun Modifier.drawnRouteGestures(
    editable: Boolean,
    interactions: () -> DrawnRouteMapInteractions,
    projection: () -> Projection?,
): Modifier = pointerInput(editable) {
    if (!editable) return@pointerInput
    // The default marker is anchored at its tip and drawn above it.
    val waypointHitArea = Rect(
        left = -24.dp.toPx(),
        top = -56.dp.toPx(),
        right = 24.dp.toPx(),
        bottom = 16.dp.toPx(),
    )
    var lastTapUptime: Long? = null
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val map = projection() ?: return@awaitEachGesture
        val current = interactions()
        val grabbed = current.preview.waypoints.indexOfLast { waypoint ->
            waypointHitArea.contains(down.position - map.screenOffsetOf(waypoint))
        }
        // A quick second tap would otherwise zoom the map.
        val followsTap = lastTapUptime?.let { lastTap ->
            down.uptimeMillis - lastTap < viewConfiguration.doubleTapTimeoutMillis
        } == true
        if (grabbed >= 0 || followsTap) down.consume()

        if (grabbed >= 0) {
            val start = map.screenOffsetOf(current.preview.waypoints[grabbed])
            var dragged = Offset.Zero
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes
                    .firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) break
                dragged += change.positionChange()
                interactions().onMoveWaypoint(grabbed, map.coordinateAt(start + dragged))
            }
            return@awaitEachGesture
        }

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.size > 1) return@awaitEachGesture
            val change = event.changes.first()
            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                return@awaitEachGesture
            }
            if (!change.pressed) {
                if (change.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis) {
                    lastTapUptime = change.uptimeMillis
                    interactions().onMapClick(map.coordinateAt(change.position))
                }
                return@awaitEachGesture
            }
        }
    }
}

private fun Projection.screenOffsetOf(waypoint: GeoCoordinate): Offset =
    toScreenLocation(LatLng(waypoint.latitude, waypoint.longitude))
        .let { Offset(it.x.toFloat(), it.y.toFloat()) }

private fun Projection.coordinateAt(offset: Offset): GeoCoordinate =
    fromScreenLocation(Point(offset.x.toInt(), offset.y.toInt()))
        .let { GeoCoordinate(it.latitude, it.longitude) }
