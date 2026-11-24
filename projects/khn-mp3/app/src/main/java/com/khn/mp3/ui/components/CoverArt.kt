package com.khn.mp3.ui.components

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.khn.mp3.data.CoverStyle
import com.khn.mp3.data.Track
import com.khn.mp3.ui.theme.KhnColors

/**
 * Grande pochette du lecteur, en style carré, arrondi ou vinyle.
 * Le vinyle tourne pendant la lecture (rotation lue au moment du dessin : aucune recomposition).
 */
@Composable
fun PlayerCover(
    track: Track?,
    fallbackUri: Uri?,
    seed: String,
    style: CoverStyle,
    isPlaying: Boolean,
    glow: Color,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        if (isPlaying || style == CoverStyle.VINYL) 1f else 0.9f,
        spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "coverScale",
    )
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(isPlaying, style) {
        if (style != CoverStyle.VINYL || !isPlaying) return@LaunchedEffect
        while (true) {
            rotation.animateTo(rotation.value + 360f, tween(14_000, easing = LinearEasing))
            rotation.snapTo(rotation.value % 360f)
        }
    }
    val shape = when (style) {
        CoverStyle.SQUARE -> RoundedCornerShape(4.dp)
        CoverStyle.ROUNDED -> RoundedCornerShape(28.dp)
        CoverStyle.VINYL -> CircleShape
    }
    Box(
        modifier
            .aspectRatio(1f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(32.dp, shape, ambientColor = glow, spotColor = glow),
        contentAlignment = Alignment.Center,
    ) {
        TrackArtwork(
            track = track,
            fallbackUri = fallbackUri,
            seed = seed,
            shape = shape,
            iconSize = 96.dp,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = if (style == CoverStyle.VINYL) rotation.value else 0f },
        )
        if (style == CoverStyle.VINYL) {
            // Sillons + étiquette centrale du disque.
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2
                for (i in 1..7) {
                    drawCircle(Color.Black.copy(alpha = 0.18f), radius = r * (0.42f + i * 0.08f), style = Stroke(1.dp.toPx()))
                }
                drawCircle(Color.Black.copy(alpha = 0.35f), radius = r * 0.2f)
                drawCircle(glow.copy(alpha = 0.9f), radius = r * 0.12f)
                drawCircle(KhnColors.Background, radius = r * 0.035f)
            }
        }
    }
}
