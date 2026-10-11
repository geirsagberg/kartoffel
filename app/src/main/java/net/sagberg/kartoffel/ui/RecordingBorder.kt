package net.sagberg.kartoffel.ui

import android.view.RoundedCorner
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import net.sagberg.kartoffel.settings.CoverageOutlineStyle
import net.sagberg.kartoffel.settings.color

/** An edge-to-edge reminder that does not consume map or control gestures. */
@Composable
internal fun RecordingBorder(isRecording: Boolean, coverageOutline: CoverageOutlineStyle) {
    val transition = updateTransition(isRecording, label = "Recording border")
    val opacity = transition.animateFloat(
        transitionSpec = { tween(if (targetState) 600 else 400) },
        label = "Border opacity",
    ) { recording -> if (recording) 1f else 0f }
    if (transition.currentState || transition.targetState) {
        val view = LocalView.current
        val density = LocalDensity.current
        val inset = 2.dp
        val strokeWidth = 2.dp
        val borderColor = coverageOutline.color()
        var shape by remember(view, density) {
            mutableStateOf(AbsoluteRoundedCornerShape(27.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    val insets = view.rootWindowInsets ?: return@onGloballyPositioned
                    // Android reports physical corners, independent of the layout direction.
                    fun radius(position: Int) = with(density) {
                        val pixels = insets.getRoundedCorner(position)?.radius ?: 0
                        (pixels.toDp() - inset - strokeWidth / 2).coerceAtLeast(0.dp)
                    }
                    shape = AbsoluteRoundedCornerShape(
                        topLeft = radius(RoundedCorner.POSITION_TOP_LEFT),
                        topRight = radius(RoundedCorner.POSITION_TOP_RIGHT),
                        bottomRight = radius(RoundedCorner.POSITION_BOTTOM_RIGHT),
                        bottomLeft = radius(RoundedCorner.POSITION_BOTTOM_LEFT),
                    )
                }
                .padding(inset)
                .graphicsLayer {
                    // Read animation state during drawing, without recomposing each frame.
                    alpha = opacity.value
                    // This layer only draws a border, so no offscreen buffer is needed for the fade.
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
                .drawWithCache {
                    val stroke = strokeWidth.toPx()
                    val outline = shape.createOutline(
                        Size(
                            (size.width - stroke).coerceAtLeast(0f),
                            (size.height - stroke).coerceAtLeast(0f),
                        ),
                        layoutDirection,
                        this,
                    )
                    val style = Stroke(stroke)
                    onDrawBehind {
                        translate(stroke / 2, stroke / 2) {
                            drawOutline(outline, borderColor, style = style)
                        }
                    }
                }
                .testTag("recording_border"),
        )
    }
}
