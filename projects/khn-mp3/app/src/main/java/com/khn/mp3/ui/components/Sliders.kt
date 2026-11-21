package com.khn.mp3.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.khn.mp3.ui.theme.KhnColors

@Composable
fun NeonSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        modifier = modifier,
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = KhnColors.Violet,
            inactiveTrackColor = KhnColors.Outline,
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
        ),
    )
}

/**
 * Curseur vertical d'égaliseur, valeur dans [-1, 1] (0 = neutre).
 * La valeur locale suit le doigt ; [onChangeFinished] n'est appelé qu'au relâchement pour limiter les écritures.
 */
@Composable
fun EqBandSlider(
    value: Float,
    onChange: (Float) -> Unit,
    onChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val currentOnChange by rememberUpdatedState(onChange)
    val currentOnFinished by rememberUpdatedState(onChangeFinished)
    var height by remember { mutableFloatStateOf(1f) }

    fun valueAt(y: Float) = (1f - 2f * (y / height)).coerceIn(-1f, 1f)

    Canvas(
        modifier
            .width(36.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { currentOnChange(valueAt(it.y)); currentOnFinished() }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectVerticalDragGestures(
                    onDragStart = { currentOnChange(valueAt(it.y)) },
                    onDragEnd = { currentOnFinished() },
                    onDragCancel = { currentOnFinished() },
                ) { change, _ ->
                    change.consume()
                    currentOnChange(valueAt(change.position.y))
                }
            },
    ) {
        height = size.height
        val cx = size.width / 2
        val trackW = 6.dp.toPx()
        val mid = size.height / 2
        val y = mid - value * mid
        // Rail
        drawRoundRect(
            color = KhnColors.Outline,
            topLeft = Offset(cx - trackW / 2, 0f),
            size = Size(trackW, size.height),
            cornerRadius = CornerRadius(trackW / 2),
        )
        // Remplissage depuis le centre
        val top = minOf(mid, y)
        val h = kotlin.math.abs(mid - y)
        if (h > 0f) {
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(KhnColors.Magenta, KhnColors.Violet), startY = top, endY = top + h),
                topLeft = Offset(cx - trackW / 2, top),
                size = Size(trackW, h),
                cornerRadius = CornerRadius(trackW / 2),
                alpha = if (enabled) 1f else 0.35f,
            )
        }
        // Ligne zéro
        drawLine(KhnColors.TextMuted.copy(alpha = 0.4f), Offset(cx - 10.dp.toPx(), mid), Offset(cx + 10.dp.toPx(), mid), 1.dp.toPx())
        // Halo + curseur
        drawCircle(KhnColors.Violet.copy(alpha = if (enabled) 0.25f else 0.1f), radius = 14.dp.toPx(), center = Offset(cx, y))
        drawCircle(if (enabled) Color.White else KhnColors.TextMuted, radius = 8.dp.toPx(), center = Offset(cx, y))
    }
}
