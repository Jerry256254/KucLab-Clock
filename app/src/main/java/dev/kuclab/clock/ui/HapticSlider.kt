package dev.kuclab.clock.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * A modern linear slider replacing the old rotary "bezel" dials: drag anywhere along the
 * track to set [value] within [valueRange], snapped to [stepSize], with a haptic tick each
 * time the snapped value changes. Fill and thumb settle into place with the app's shared
 * [claudeSpring] so every drag ends with the same soft, calm motion.
 */
@Composable
fun HapticSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    stepSize: Float = 1f,
    enabled: Boolean = true,
    trackHeight: Dp = 10.dp,
    thumbSize: Dp = 26.dp
) {
    val haptics = LocalHapticFeedback.current
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)

    fun setFromX(x: Float, widthPx: Float) {
        val fraction = (x / widthPx).coerceIn(0f, 1f)
        val raw = valueRange.start + fraction * span
        val snapped = ((raw / stepSize).roundToInt() * stepSize)
            .coerceIn(valueRange.start, valueRange.endInclusive)
        if (snapped != currentValue) {
            haptics.tap()
            currentOnValueChange(snapped)
        }
    }

    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(fraction, claudeSpring(), label = "sliderFraction")

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(thumbSize)
            .then(
                if (enabled) {
                    Modifier.pointerInput(valueRange, stepSize) {
                        detectDragGestures(
                            onDragStart = { offset -> setFromX(offset.x, size.width.toFloat()) },
                            onDrag = { change, _ -> change.consume(); setFromX(change.position.x, size.width.toFloat()) }
                        )
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(50))
                .background(TrackBg)
        )
        Box(
            Modifier
                .fillMaxWidth(animatedFraction.coerceIn(0.001f, 1f))
                .height(trackHeight)
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(50))
                .background(Accent)
        )
        val thumbTravel = maxWidth - thumbSize
        val thumbOffset = thumbTravel * animatedFraction
        Box(
            Modifier
                .offset(x = thumbOffset)
                .align(Alignment.CenterStart)
                .size(thumbSize)
                .clip(CircleShape)
                .background(CardBg)
                .border(2.dp, Accent, CircleShape)
        )
    }
}
