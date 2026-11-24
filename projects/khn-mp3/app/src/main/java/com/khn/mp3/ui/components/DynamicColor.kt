package com.khn.mp3.ui.components

import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val accentCache = LruCache<String, Int>(64)

/**
 * Couleur d'accent tirée de la pochette (couleur vive dominante), rendue lisible sur fond sombre.
 * Null si désactivé ou sans pochette.
 */
@Composable
fun rememberArtworkAccent(model: Any?, enabled: Boolean): Color? {
    val context = LocalContext.current
    val key = model?.toString()
    var color by remember(key) { mutableStateOf(key?.let { accentCache.get(it) }?.let(::Color)) }
    LaunchedEffect(key, enabled) {
        if (!enabled || model == null || key == null) {
            color = null
            return@LaunchedEffect
        }
        accentCache.get(key)?.let { color = Color(it); return@LaunchedEffect }
        val request = ImageRequest.Builder(context).data(model).size(160).allowHardware(false).build()
        val result = context.imageLoader.execute(request)
        val bitmap = ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
        if (bitmap == null) {
            color = null
            return@LaunchedEffect
        }
        val rgb = withContext(Dispatchers.Default) {
            val p = Palette.from(bitmap).maximumColorCount(16).generate()
            val swatch = p.vibrantSwatch ?: p.lightVibrantSwatch ?: p.mutedSwatch ?: p.dominantSwatch
            swatch?.rgb?.let(::readableOnDark)
        }
        if (rgb != null) accentCache.put(key, rgb)
        color = rgb?.let(::Color)
    }
    return color
}

/** Ajuste saturation et luminosité pour rester vif et lisible sur le fond noir de l'app. */
private fun readableOnDark(rgb: Int): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(rgb, hsl)
    hsl[1] = hsl[1].coerceIn(0.45f, 0.95f)
    hsl[2] = hsl[2].coerceIn(0.50f, 0.68f)
    return ColorUtils.HSLToColor(hsl)
}
