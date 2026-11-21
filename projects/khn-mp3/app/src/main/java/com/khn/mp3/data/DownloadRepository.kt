package com.khn.mp3.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Téléchargements via le DownloadManager système vers Musique/KHN MP3/ (notification de progression incluse). */
class DownloadRepository(context: Context, private val userData: UserDataRepository) {

    private val dm = context.getSystemService(DownloadManager::class.java)

    @Immutable
    data class Status(
        val id: Long,
        val title: String,
        val state: State,
        val downloaded: Long,
        val total: Long,
    ) {
        val progress: Float get() = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
    }

    enum class State { PENDING, RUNNING, PAUSED, DONE, FAILED }

    /** @return null si l'URL n'est pas valide. */
    fun enqueue(url: String, title: String? = null, fileName: String? = null): Long? {
        val trimmed = url.trim()
        if (!URLUtil.isHttpUrl(trimmed) && !URLUtil.isHttpsUrl(trimmed)) return null
        val uri = Uri.parse(trimmed)
        val ext = MimeTypeMap.getFileExtensionFromUrl(trimmed).lowercase().ifEmpty { "mp3" }
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "audio/mpeg"
        val rawName = fileName ?: URLUtil.guessFileName(trimmed, null, mime)
        val safeName = sanitize(rawName).let { n ->
            if (n.contains('.') && n.substringAfterLast('.').length in 2..4) n else "$n.mp3"
        }
        val displayTitle = title ?: safeName.substringBeforeLast('.')

        val request = DownloadManager.Request(uri)
            .setTitle(displayTitle)
            .setDescription("KHN MP3")
            .setMimeType(if (mime.startsWith("audio/")) mime else "audio/mpeg")
            .addRequestHeader("User-Agent", USER_AGENT)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "${Track.DOWNLOAD_FOLDER}/$safeName")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        @Suppress("DEPRECATION")
        request.allowScanningByMediaScanner()

        val id = dm.enqueue(request)
        userData.addDownload(DownloadEntry(id, displayTitle, System.currentTimeMillis()))
        return id
    }

    suspend fun statuses(entries: List<DownloadEntry>): List<Status> = withContext(Dispatchers.IO) {
        if (entries.isEmpty()) return@withContext emptyList()
        val byId = HashMap<Long, Status>()
        val query = DownloadManager.Query().setFilterById(*entries.map { it.id }.toLongArray())
        runCatching {
            dm.query(query)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
                val statusCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                val soFarCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val state = when (c.getInt(statusCol)) {
                        DownloadManager.STATUS_PENDING -> State.PENDING
                        DownloadManager.STATUS_RUNNING -> State.RUNNING
                        DownloadManager.STATUS_PAUSED -> State.PAUSED
                        DownloadManager.STATUS_SUCCESSFUL -> State.DONE
                        else -> State.FAILED
                    }
                    byId[id] = Status(id, "", state, c.getLong(soFarCol), c.getLong(totalCol))
                }
            }
        }
        entries.map { e ->
            byId[e.id]?.copy(title = e.title) ?: Status(e.id, e.title, State.FAILED, 0, 0)
        }
    }

    fun cancel(id: Long) {
        runCatching { dm.remove(id) }
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(120).ifEmpty { "musique" }
}
