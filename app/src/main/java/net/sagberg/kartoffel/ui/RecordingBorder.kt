package net.sagberg.kartoffel.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import net.sagberg.kartoffel.settings.CoverageOutlineStyle
import net.sagberg.kartoffel.settings.color

/** An edge-to-edge reminder that does not consume map or control gestures. */
@Composable
internal fun RecordingBorder(isRecording: Boolean, coverageOutline: CoverageOutlineStyle) {
    if (isRecording) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp)
                .border(2.dp, coverageOutline.color(), RoundedCornerShape(28.dp))
                .testTag("recording_border"),
        )
    }
}
