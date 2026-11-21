package com.khn.mp3.data

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Catalogue de musique libre de droits d'Internet Archive (archive.org).
 * Seuls les éléments sous licence Creative Commons / domaine public ou issus des netlabels sont proposés.
 */
class ArchiveRepository {

    @Immutable
    data class Item(val identifier: String, val title: String, val creator: String)

    @Immutable
    data class AudioFile(
        val title: String,
        val creator: String,
        val fileName: String,
        val url: String,
        val durationMs: Long,
        val sizeBytes: Long,
    )

    suspend fun search(query: String, page: Int = 1): List<Item> = withContext(Dispatchers.IO) {
        val clean = query.replace(Regex("[\"():\\[\\]{}^~*?\\\\/]"), " ").trim()
        if (clean.isEmpty()) return@withContext emptyList()
        val q = "($clean) AND mediatype:(audio) AND " +
            "(collection:(netlabels) OR licenseurl:(*creativecommons* OR *publicdomain*))"
        val url = buildString {
            append("https://archive.org/advancedsearch.php?q=")
            append(enc(q))
            append("&fl[]=identifier&fl[]=title&fl[]=creator")
            append("&sort[]=downloads+desc&rows=40&page=$page&output=json")
        }
        val docs = JSONObject(get(url)).getJSONObject("response").getJSONArray("docs")
        (0 until docs.length()).map { i ->
            val d = docs.getJSONObject(i)
            Item(
                identifier = d.getString("identifier"),
                title = d.text("title") ?: d.getString("identifier"),
                creator = d.text("creator") ?: Track.UNKNOWN_ARTIST,
            )
        }
    }

    suspend fun files(item: Item): List<AudioFile> = withContext(Dispatchers.IO) {
        val root = JSONObject(get("https://archive.org/metadata/${enc(item.identifier)}"))
        val files = root.optJSONArray("files") ?: return@withContext emptyList()
        val all = (0 until files.length()).map { files.getJSONObject(it) }
            .filter { it.optString("name").endsWith(".mp3", ignoreCase = true) }
        // Quand plusieurs débits existent, on garde la meilleure version (VBR / 320 / 256 / 128).
        val preferred = all.filterNot { it.optString("format").contains("64Kbps", ignoreCase = true) }
            .ifEmpty { all }
        preferred.map { f ->
            val name = f.getString("name")
            AudioFile(
                title = f.text("title") ?: name.substringAfterLast('/').substringBeforeLast('.'),
                creator = f.text("creator") ?: f.text("artist") ?: item.creator,
                fileName = name.substringAfterLast('/'),
                url = "https://archive.org/download/${enc(item.identifier)}/" +
                    name.split('/').joinToString("/") { enc(it) },
                durationMs = parseLength(f.optString("length")),
                sizeBytes = f.optString("size").toLongOrNull() ?: 0L,
            )
        }
    }

    private fun parseLength(raw: String): Long = parseClock(raw)

    private fun JSONObject.text(key: String): String? = when (val v = opt(key)) {
        is String -> v.takeIf { it.isNotBlank() }
        is JSONArray -> (0 until v.length()).joinToString(", ") { v.optString(it) }.takeIf { it.isNotBlank() }
        else -> null
    }

    private fun enc(s: String) = urlEncode(s)

    private fun get(url: String): String = httpGet(url)
}
