package com.khn.mp3.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Un titre de la file d'attente sauvegardée (autonome : pas besoin de la bibliothèque pour reprendre). */
@Serializable
data class SavedItem(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artwork: String? = null,
)

/** Dernière session d'écoute : file, titre en cours, position, modes. */
@Serializable
data class PlaybackSnapshot(
    val items: List<SavedItem> = emptyList(),
    val index: Int = 0,
    val positionMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = 0,
)

/** Sauvegarde / relecture de la session (reprise de lecture au redémarrage). */
class SnapshotStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "session.json"))
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): PlaybackSnapshot? = runCatching {
        if (!file.baseFile.exists()) null
        else json.decodeFromString(PlaybackSnapshot.serializer(), file.readFully().decodeToString())
    }.getOrNull()?.takeIf { it.items.isNotEmpty() }

    @Synchronized
    fun save(snapshot: PlaybackSnapshot) {
        val stream = runCatching { file.startWrite() }.getOrNull() ?: return
        try {
            stream.write(json.encodeToString(PlaybackSnapshot.serializer(), snapshot).encodeToByteArray())
            file.finishWrite(stream)
        } catch (_: Exception) {
            file.failWrite(stream)
        }
    }
}
