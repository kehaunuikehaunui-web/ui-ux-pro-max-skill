package com.khn.mp3.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.InputStream
import kotlin.coroutines.resume

/**
 * « Partager vers KHN MP3 » : copie les fichiers audio reçus (WhatsApp, Telegram, Drive, e-mail…)
 * dans Musique/KHN MP3. Une copie est nécessaire : l'accès accordé par le partage est temporaire.
 */
class SharedAudioImporter(private val context: Context) {

    suspend fun import(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        uris.count { src -> runCatching { copy(src) }.getOrDefault(false) }
    }

    private suspend fun copy(src: Uri): Boolean {
        val resolver = context.contentResolver
        val mime = resolver.getType(src)?.substringBefore(';')?.takeIf { it.startsWith("audio/") } ?: "audio/mpeg"
        val rawName = runCatching {
            resolver.query(src, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: "audio_${System.currentTimeMillis()}"
        return saveToMusic(context, { resolver.openInputStream(src) }, rawName, mime) != null
    }
}

/**
 * Écrit un fichier audio dans Musique/KHN MP3 et l'enregistre auprès d'Android.
 * @return l'URI MediaStore du nouveau fichier, ou null en cas d'échec.
 */
internal suspend fun saveToMusic(
    context: Context,
    open: () -> InputStream?,
    rawName: String,
    mime: String,
): Uri? = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "mp3"
    val clean = rawName.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").take(120).ifBlank { "musique" }
    val fileName = if (clean.contains('.')) clean else "$clean.$ext"

    if (Build.VERSION.SDK_INT >= 29) {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/${Track.DOWNLOAD_FOLDER}")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val dest = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: return@withContext null
        return@withContext try {
            val input = open() ?: error("lecture impossible")
            input.use { i -> resolver.openOutputStream(dest)?.use { o -> i.copyTo(o) } ?: error("écriture impossible") }
            resolver.update(dest, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            dest
        } catch (e: Exception) {
            runCatching { resolver.delete(dest, null, null) }
            null
        }
    }

    // Android 8 / 9 : écriture directe (permission de stockage demandée au premier lancement).
    @Suppress("DEPRECATION")
    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), Track.DOWNLOAD_FOLDER)
    if (!dir.exists() && !dir.mkdirs()) return@withContext null
    var out = File(dir, fileName)
    var n = 1
    while (out.exists()) out = File(dir, "${fileName.substringBeforeLast('.')} ($n).${fileName.substringAfterLast('.')}").also { n++ }
    val ok = runCatching {
        val input = open() ?: error("lecture impossible")
        input.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
    }.isSuccess
    if (!ok) {
        out.delete()
        return@withContext null
    }
    // On attend l'indexation pour connaître l'identifiant MediaStore du fichier.
    withTimeoutOrNull(8_000) {
        suspendCancellableCoroutine { cont ->
            MediaScannerConnection.scanFile(context, arrayOf(out.absolutePath), arrayOf(mime)) { _, uri ->
                if (cont.isActive) cont.resume(uri)
            }
        }
    } ?: Uri.fromFile(out)
}
