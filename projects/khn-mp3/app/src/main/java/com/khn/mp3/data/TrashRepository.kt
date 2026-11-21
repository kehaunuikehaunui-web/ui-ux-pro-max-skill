package com.khn.mp3.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Corbeille de KHN MP3 : 30 jours pour récupérer une musique supprimée.
 *
 * - Android 11+ : corbeille d'Android (le fichier reste en place, masqué) ; on garde ici sa fiche.
 * - Android 8–10, ou fichier importé hors bibliothèque Android : copie dans le stockage privé de l'app.
 */
class TrashRepository(private val context: Context, private val userData: UserDataRepository) {

    private val dir: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "corbeille").apply { mkdirs() }

    /** Fiche d'une musique mise à la corbeille, avec ses favoris et playlists. */
    fun entryFor(track: Track, system: Boolean, mediaUri: Uri?, file: File?, mime: String, fileName: String): TrashEntry {
        val d = userData.data.value
        val now = System.currentTimeMillis()
        return TrashEntry(
            id = UUID.randomUUID().toString(),
            system = system,
            mediaUri = mediaUri?.toString(),
            file = file?.absolutePath,
            mime = mime,
            fileName = fileName,
            title = track.title,
            artist = track.artist,
            album = track.album,
            durationMs = track.durationMs,
            originalId = track.id,
            deletedAt = now,
            expiresAt = now + RETENTION_MS,
            favorite = track.id in d.favorites,
            playlists = d.playlists.filter { track.id in it.trackIds }.map { it.id },
            imported = d.imported.firstOrNull { "doc:${it.uri}" == track.id },
        )
    }

    /** Corbeille interne : copie le fichier avant sa suppression. Null si la copie échoue. */
    suspend fun stageCopy(track: Track): TrashEntry? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(track.uri)?.substringBefore(';')?.takeIf { it.startsWith("audio/") } ?: "audio/mpeg"
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "mp3"
        val name = runCatching {
            resolver.query(track.uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: "${track.title}.$ext"
        val out = File(dir, "${UUID.randomUUID()}.$ext")
        val ok = runCatching {
            val input = resolver.openInputStream(track.uri) ?: error("lecture impossible")
            input.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
        }.isSuccess
        if (!ok) {
            out.delete()
            return@withContext null
        }
        entryFor(track, system = false, mediaUri = null, file = out, mime = mime, fileName = name)
    }

    /** La copie n'a pas servi (suppression refusée ou impossible). */
    fun discard(entry: TrashEntry) {
        entry.file?.let { runCatching { File(it).delete() } }
    }

    /** Corbeille interne : l'original est supprimé, on enregistre la fiche. */
    fun commitCopies(entries: List<TrashEntry>) {
        if (entries.isEmpty()) return
        userData.addTrash(entries)
        // L'identifiant changera à la restauration : favoris et playlists sont mémorisés dans la fiche.
        userData.forgetTracks(entries.mapTo(HashSet()) { it.originalId })
    }

    /** Corbeille Android : le fichier est masqué mais garde son identifiant (favoris et playlists conservés). */
    fun commitSystem(entries: List<TrashEntry>) {
        if (entries.isEmpty()) return
        userData.addTrash(entries)
        userData.removeImportedIds(entries.filter { it.imported != null }.mapTo(HashSet()) { it.originalId })
    }

    /** Corbeille interne : remet les fichiers dans Musique/KHN MP3. @return nombre de musiques restaurées. */
    suspend fun restoreCopies(entries: List<TrashEntry>): Int {
        val restored = HashSet<String>()
        for (e in entries) {
            val file = e.file?.let(::File)?.takeIf { it.exists() } ?: continue
            val uri = saveToMusic(context, { file.inputStream() }, e.fileName, e.mime) ?: continue
            if (uri.authority == "media") {
                val newId = "ms:${ContentUris.parseId(uri)}"
                userData.restoreMembership(newId, e.favorite, e.playlists)
            }
            file.delete()
            restored += e.id
        }
        userData.removeTrash(restored)
        return restored.size
    }

    /** Corbeille Android : Android a restauré les fichiers. */
    fun onSystemRestored(entries: List<TrashEntry>) {
        userData.removeTrash(entries.mapTo(HashSet()) { it.id })
        userData.addImported(entries.mapNotNull { it.imported })
    }

    /** Suppression définitive depuis la corbeille. */
    fun onDeletedForever(entries: List<TrashEntry>) {
        entries.forEach(::discard)
        userData.removeTrash(entries.mapTo(HashSet()) { it.id })
        userData.forgetTracks(entries.mapTo(HashSet()) { it.originalId })
    }

    /** Efface ce qui a dépassé 30 jours (Android purge sa propre corbeille de son côté). */
    fun purgeExpired() {
        val now = System.currentTimeMillis()
        val expired = userData.data.value.trash.filter { it.expiresAt < now }
        if (expired.isNotEmpty()) onDeletedForever(expired)
    }

    companion object {
        val RETENTION_MS = TimeUnit.DAYS.toMillis(30)
    }
}
