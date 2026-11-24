package com.khn.mp3.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import com.khn.mp3.data.SavedItem
import com.khn.mp3.data.Track
import java.io.File

/** Titre de la bibliothèque → élément du lecteur (pochette personnalisée incluse pour la notification). */
@OptIn(UnstableApi::class)
fun Track.toMediaItem(coverPath: String? = null): MediaItem = buildItem(
    id = id,
    uri = uri,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    artwork = coverPath?.let { Uri.fromFile(File(it)) },
)

fun SavedItem.toMediaItem(): MediaItem = buildItem(
    id = id,
    uri = Uri.parse(uri),
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    artwork = artwork?.let(Uri::parse),
)

@OptIn(UnstableApi::class)
fun MediaItem.toSavedItem(): SavedItem? {
    val uri = localConfiguration?.uri ?: requestMetadata.mediaUri ?: return null
    return SavedItem(
        id = mediaId,
        uri = uri.toString(),
        title = mediaMetadata.title?.toString().orEmpty(),
        artist = mediaMetadata.artist?.toString().orEmpty(),
        album = mediaMetadata.albumTitle?.toString().orEmpty(),
        durationMs = mediaMetadata.durationMs ?: 0L,
        artwork = mediaMetadata.artworkUri?.toString(),
    )
}

@OptIn(UnstableApi::class)
private fun buildItem(
    id: String,
    uri: Uri,
    title: String,
    artist: String,
    album: String,
    durationMs: Long,
    artwork: Uri?,
): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(uri)
    .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setDurationMs(durationMs.takeIf { it > 0 })
            .setArtworkUri(artwork)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .build(),
    )
    .build()
