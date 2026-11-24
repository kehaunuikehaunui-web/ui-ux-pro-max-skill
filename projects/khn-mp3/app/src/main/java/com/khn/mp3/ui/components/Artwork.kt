package com.khn.mp3.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.ImageRequest
import coil.request.Options
import coil.size.Dimension
import androidx.compose.runtime.staticCompositionLocalOf
import com.khn.mp3.data.Track
import com.khn.mp3.data.coverFor
import com.khn.mp3.ui.theme.Khn
import java.io.File
import java.io.IOException
import kotlin.math.absoluteValue

/** Requête de pochette d'album pour Coil. */
data class ArtworkRequest(val uri: Uri)

/** Charge la pochette intégrée au fichier (MediaStore sur Android 10+, sinon tags ID3). */
class ArtworkFetcher(
    private val data: ArtworkRequest,
    private val options: Options,
    private val context: Context,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val px = ((options.size.width as? Dimension.Pixels)?.px ?: 512).coerceIn(96, 1024)
        val bitmap = loadThumbnail(px) ?: loadEmbedded(px) ?: throw IOException("Pas de pochette")
        return DrawableResult(
            drawable = BitmapDrawable(context.resources, bitmap),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    private fun loadThumbnail(px: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < 29 || data.uri.authority != "media") return null
        return runCatching { context.contentResolver.loadThumbnail(data.uri, Size(px, px), null) }.getOrNull()
    }

    private fun loadEmbedded(px: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, data.uri)
            val bytes = retriever.embeddedPicture ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= px && bounds.outHeight / (sample * 2) >= px) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<ArtworkRequest> {
        override fun create(data: ArtworkRequest, options: Options, imageLoader: ImageLoader): Fetcher =
            ArtworkFetcher(data, options, context)
    }

    class Key : Keyer<ArtworkRequest> {
        override fun key(data: ArtworkRequest, options: Options): String = "art:${data.uri}"
    }
}

/** Pochettes personnalisées de l'utilisateur (clé → fichier), fournies à tout l'écran. */
val LocalCovers = staticCompositionLocalOf<Map<String, String>> { emptyMap() }

/** Source d'image : pochette personnalisée en priorité, sinon pochette intégrée au fichier. */
fun artworkModel(uri: Uri?, customFile: String?): Any? =
    customFile?.let(::File)?.takeIf { it.exists() } ?: uri?.let(::ArtworkRequest)

/** Pochette d'un titre, en tenant compte des pochettes personnalisées (titre puis album). */
@Composable
fun TrackArtwork(
    track: Track?,
    modifier: Modifier = Modifier,
    fallbackUri: Uri? = null,
    seed: String = track?.id.orEmpty(),
    shape: Shape = Khn.shapes.artwork,
    iconSize: Dp = 22.dp,
) {
    val covers = LocalCovers.current
    Artwork(
        uri = track?.uri ?: fallbackUri,
        seed = seed,
        modifier = modifier,
        shape = shape,
        iconSize = iconSize,
        customFile = covers.coverFor(track),
    )
}

private val placeholderGradients = listOf(
    listOf(Color(0xFF6D28D9), Color(0xFFDB2777)),
    listOf(Color(0xFF4338CA), Color(0xFF06B6D4)),
    listOf(Color(0xFF9333EA), Color(0xFFF97316)),
    listOf(Color(0xFFBE185D), Color(0xFF7C3AED)),
    listOf(Color(0xFF0E7490), Color(0xFF8B5CF6)),
)

/** Pochette avec repli dégradé néon (couleur stable par morceau). */
@Composable
fun Artwork(
    uri: Uri?,
    seed: String,
    modifier: Modifier = Modifier,
    shape: Shape = Khn.shapes.artwork,
    iconSize: Dp = 22.dp,
    customFile: String? = null,
) {
    val context = LocalContext.current
    val request = remember(uri, customFile) {
        artworkModel(uri, customFile)?.let { ImageRequest.Builder(context).data(it).build() }
    }
    // Le dégradé est dessiné dessous ; l'image le recouvre une fois chargée (pas de sous-composition).
    Box(modifier.clip(shape)) {
        Placeholder(seed, iconSize)
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun Placeholder(seed: String, iconSize: Dp) {
    val colors = placeholderGradients[seed.hashCode().absoluteValue % placeholderGradients.size]
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(iconSize),
        )
    }
}
