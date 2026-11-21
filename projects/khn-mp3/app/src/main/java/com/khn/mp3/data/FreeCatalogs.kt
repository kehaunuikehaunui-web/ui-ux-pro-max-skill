package com.khn.mp3.data

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Morceau d'un catalogue de musique libre, téléchargeable directement. */
@Immutable
data class FreeTrack(
    val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val streamUrl: String,
    val downloadUrl: String,
    val fileName: String,
    val license: String,
)

/** Musique libre de ccMixter (remix, électro, chill…), Creative Commons, sans inscription. */
class CcMixterRepository {

    suspend fun search(query: String): List<FreeTrack> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val url = "https://ccmixter.org/api/query?f=json&search_type=any&limit=40&search=${urlEncode(q)}"
        val arr = JSONArray(httpGet(url))
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::parse) }
    }

    private fun parse(o: JSONObject): FreeTrack? {
        val files = o.optJSONArray("files") ?: return null
        val file = (0 until files.length()).mapNotNull { files.optJSONObject(it) }.firstOrNull { f ->
            val ext = f.optJSONObject("file_format_info")?.optString("default-ext").orEmpty()
            ext.equals("mp3", true) || f.optString("file_name").endsWith(".mp3", true) ||
                f.optString("download_url").endsWith(".mp3", true)
        } ?: return null
        val url = https(file.optString("download_url")).takeIf { it.isNotBlank() } ?: return null
        val title = o.optString("upload_name").ifBlank { file.optString("file_name").substringBeforeLast('.') }
        val artist = o.optString("user_real_name").ifBlank { o.optString("user_name") }.ifBlank { Track.UNKNOWN_ARTIST }
        return FreeTrack(
            id = "ccm:" + o.optString("upload_id", url),
            title = title,
            artist = artist,
            durationMs = parseClock(file.optJSONObject("file_format_info")?.optString("ps")),
            streamUrl = url,
            downloadUrl = url,
            fileName = "$artist - $title.mp3",
            license = o.optString("license_name"),
        )
    }

    /** Le lecteur refuse le HTTP non chiffré : ccMixter sert aussi ses fichiers en HTTPS. */
    private fun https(u: String) = if (u.startsWith("http://")) "https://" + u.removePrefix("http://") else u
}

/**
 * Catalogue Jamendo (500 000+ titres sous licence Creative Commons).
 * Nécessite une clé « client_id » gratuite créée sur devportal.jamendo.com.
 */
class JamendoRepository {

    class MissingKeyException : Exception()

    suspend fun search(query: String, clientId: String?): List<FreeTrack> = withContext(Dispatchers.IO) {
        val key = clientId?.trim().orEmpty()
        if (key.isEmpty()) throw MissingKeyException()
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val url = "https://api.jamendo.com/v3.0/tracks/?client_id=${urlEncode(key)}&format=json&limit=40" +
            "&audioformat=mp32&audiodlformat=mp32&order=popularity_total&search=${urlEncode(q)}"
        val root = JSONObject(httpGet(url))
        val headers = root.optJSONObject("headers")
        if (headers != null && headers.optString("status") == "failed") {
            error(headers.optString("error_message").ifBlank { "Clé Jamendo refusée" })
        }
        val results = root.optJSONArray("results") ?: return@withContext emptyList()
        (0 until results.length()).mapNotNull { i ->
            val t = results.optJSONObject(i) ?: return@mapNotNull null
            if (!t.optBoolean("audiodownload_allowed", true)) return@mapNotNull null
            val download = t.optString("audiodownload").ifBlank { t.optString("audio") }
            if (download.isBlank()) return@mapNotNull null
            val title = t.optString("name")
            val artist = t.optString("artist_name").ifBlank { Track.UNKNOWN_ARTIST }
            FreeTrack(
                id = "jam:" + t.optString("id"),
                title = title,
                artist = artist,
                durationMs = t.optLong("duration") * 1000,
                streamUrl = t.optString("audio").ifBlank { download },
                downloadUrl = download,
                fileName = "$artist - $title.mp3",
                license = if (t.optString("license_ccurl").contains("creativecommons")) "Creative Commons" else "",
            )
        }
    }
}
