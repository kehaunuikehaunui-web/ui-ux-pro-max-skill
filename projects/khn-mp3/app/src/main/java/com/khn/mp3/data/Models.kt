package com.khn.mp3.data

import android.net.Uri
import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import java.text.Normalizer

/** Un morceau jouable : issu de MediaStore, importé via le sélecteur de fichiers, ou ouvert depuis un autre app. */
@Immutable
data class Track(
    val id: String,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val dateAdded: Long,
    val folder: String,
    val source: Source,
) {
    enum class Source { DEVICE, IMPORTED, EXTERNAL }

    /** Clé de recherche pré-calculée : minuscules, sans accents. */
    val searchKey: String by lazy(LazyThreadSafetyMode.NONE) { normalize("$title $artist $album") }

    /** Téléchargé par KHN MP3 (Musique/KHN MP3) ou depuis le navigateur (dossier Download). */
    val isDownloaded: Boolean
        get() = folder.contains(DOWNLOAD_FOLDER, ignoreCase = true) || DOWNLOAD_DIR.containsMatchIn(folder)

    companion object {
        const val DOWNLOAD_FOLDER = "KHN MP3"
        const val UNKNOWN_ARTIST = "Artiste inconnu"
        const val UNKNOWN_ALBUM = "Album inconnu"
        private val DOWNLOAD_DIR = Regex("(^|/)Download/", RegexOption.IGNORE_CASE)
    }
}

fun normalize(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val trackIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class ImportedTrack(
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val addedAt: Long,
)

@Serializable
data class DownloadEntry(
    val id: Long,
    val title: String,
    val createdAt: Long,
)

@Serializable
data class UserData(
    val favorites: List<String> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val imported: List<ImportedTrack> = emptyList(),
    val downloads: List<DownloadEntry> = emptyList(),
)

enum class SortOrder(val label: String) {
    TITLE("Titre"),
    ARTIST("Artiste"),
    RECENT("Ajout récent"),
    DURATION("Durée"),
}

const val FAVORITES_PLAYLIST_ID = "favorites"

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
