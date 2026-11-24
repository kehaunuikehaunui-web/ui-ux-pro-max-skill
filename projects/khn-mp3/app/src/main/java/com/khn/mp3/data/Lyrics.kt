package com.khn.mp3.data

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

@Immutable
data class LyricLine(val timeMs: Long, val text: String)

/** Paroles d'un titre. [synced] = horodatées (karaoké), sinon texte simple. */
@Immutable
data class Lyrics(val synced: Boolean, val lines: List<LyricLine>, val source: String) {
    val isEmpty: Boolean get() = lines.all { it.text.isBlank() }
}

/** Lecture du format LRC : « [01:23.45] texte », plusieurs horodatages par ligne, balise [offset:]. */
object LrcParser {
    private val TIME = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val OFFSET = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)

    fun parse(raw: String, source: String): Lyrics? {
        val text = raw.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val offset = OFFSET.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val synced = ArrayList<LyricLine>()
        for (line in text.lines()) {
            val stamps = TIME.findAll(line).toList()
            if (stamps.isEmpty()) continue
            val lyric = line.substring(stamps.last().range.last + 1).trim()
            for (m in stamps) {
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val fracRaw = m.groupValues[3]
                val frac = when (fracRaw.length) {
                    0 -> 0L
                    1 -> fracRaw.toLong() * 100
                    2 -> fracRaw.toLong() * 10
                    else -> fracRaw.take(3).toLong()
                }
                synced += LyricLine((min * 60_000 + sec * 1000 + frac - offset).coerceAtLeast(0), lyric)
            }
        }
        if (synced.isNotEmpty()) return Lyrics(true, synced.sortedBy { it.timeMs }, source)
        // Pas d'horodatage : texte simple (on retire les balises de métadonnées éventuelles).
        val plain = text.lines()
            .filterNot { it.trim().matches(Regex("""\[[a-zA-Z]+:.*]""")) }
            .map { LyricLine(-1, it.trim()) }
            .dropWhile { it.text.isBlank() }
            .dropLastWhile { it.text.isBlank() }
        return if (plain.isEmpty()) null else Lyrics(false, plain, source)
    }

    /** Sérialisation pour le cache. */
    fun toText(l: Lyrics): String = if (l.synced) {
        l.lines.joinToString("\n") { line ->
            val t = line.timeMs
            "[%02d:%02d.%02d]%s".format(t / 60_000, (t / 1000) % 60, (t % 1000) / 10, line.text)
        }
    } else l.lines.joinToString("\n") { it.text }
}

/** Lecture minimale des balises ID3v2 (MP3) : frame USLT = paroles intégrées au fichier. */
internal object Id3Lyrics {

    fun read(input: InputStream): String? {
        val header = input.readExactly(10) ?: return null
        if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) return null
        val version = header[3].toInt()
        if (version !in 3..4) return null
        val flags = header[5].toInt()
        var remaining = syncSafe(header, 6)
        if (flags and 0x40 != 0) { // en-tête étendu
            val ext = input.readExactly(4) ?: return null
            val extSize = if (version == 4) syncSafe(ext, 0) - 4 else bigEndian(ext, 0)
            input.skipFully(extSize.toLong())
            remaining -= 4 + extSize
        }
        while (remaining > 10) {
            val fh = input.readExactly(10) ?: return null
            remaining -= 10
            if (fh[0].toInt() == 0) return null // remplissage : fin des frames
            val id = String(fh, 0, 4, Charsets.ISO_8859_1)
            val size = if (version == 4) syncSafe(fh, 4) else bigEndian(fh, 4)
            if (size <= 0 || size > remaining) return null
            remaining -= size
            if (id == "USLT" && size < 512 * 1024) {
                val data = input.readExactly(size) ?: return null
                return decodeUslt(data)
            }
            input.skipFully(size.toLong())
        }
        return null
    }

    private fun decodeUslt(d: ByteArray): String? {
        if (d.size < 5) return null
        val enc = d[0].toInt()
        val charset: Charset = when (enc) {
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
        var i = 4 // encodage (1) + langue (3)
        // Description terminée par 0x00 (ou 0x00 0x00 en UTF-16).
        if (enc == 1 || enc == 2) {
            while (i + 1 < d.size && !(d[i].toInt() == 0 && d[i + 1].toInt() == 0)) i += 2
            i += 2
        } else {
            while (i < d.size && d[i].toInt() != 0) i++
            i += 1
        }
        if (i >= d.size) return null
        return String(d, i, d.size - i, charset).trim('\u0000', ' ', '\n').takeIf { it.isNotBlank() }
    }

    private fun syncSafe(b: ByteArray, o: Int) =
        (b[o].toInt() and 0x7F shl 21) or (b[o + 1].toInt() and 0x7F shl 14) or
            (b[o + 2].toInt() and 0x7F shl 7) or (b[o + 3].toInt() and 0x7F)

    private fun bigEndian(b: ByteArray, o: Int) =
        (b[o].toInt() and 0xFF shl 24) or (b[o + 1].toInt() and 0xFF shl 16) or
            (b[o + 2].toInt() and 0xFF shl 8) or (b[o + 3].toInt() and 0xFF)

    private fun InputStream.readExactly(n: Int): ByteArray? {
        val buf = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = read(buf, off, n - off)
            if (r < 0) return null
            off += r
        }
        return buf
    }

    private fun InputStream.skipFully(n: Long) {
        var left = n
        while (left > 0) {
            val s = skip(left)
            if (s <= 0) { if (read() < 0) return else left-- } else left -= s
        }
    }
}

/**
 * Paroles d'un titre, dans cet ordre :
 * 1. cache (paroles déjà trouvées ou importées) ;
 * 2. paroles intégrées au MP3 (ID3 USLT) ;
 * 3. fichier .lrc portant le même nom que la musique (quand Android en autorise la lecture) ;
 * 4. LRCLIB (lrclib.net, base collaborative gratuite), résultat mis en cache pour l'écoute hors ligne.
 */
class LyricsRepository(private val context: Context) {

    private val dir: File get() = File(context.filesDir, "lyrics").apply { mkdirs() }

    private fun key(trackId: String): String =
        MessageDigest.getInstance("SHA-1").digest(trackId.toByteArray()).joinToString("") { "%02x".format(it) }

    suspend fun load(track: Track, allowOnline: Boolean = true): Lyrics? = withContext(Dispatchers.IO) {
        val k = key(track.id)
        val cached = File(dir, "$k.lrc")
        if (cached.exists()) return@withContext LrcParser.parse(cached.readText(), "Enregistrées")

        runCatching { context.contentResolver.openInputStream(track.uri)?.use { Id3Lyrics.read(it.buffered()) } }
            .getOrNull()?.let { raw -> LrcParser.parse(raw, "Intégrées au fichier")?.let { return@withContext it } }

        sidecarLrc(track)?.let { raw -> LrcParser.parse(raw, "Fichier .lrc")?.let { save(track, it); return@withContext it } }

        if (!allowOnline) return@withContext null
        val miss = File(dir, "$k.none")
        if (miss.exists() && System.currentTimeMillis() - miss.lastModified() < TimeUnit.DAYS.toMillis(7)) return@withContext null
        val online = runCatching { fetchLrclib(track) }.getOrNull()
        if (online != null) save(track, online) else runCatching { miss.writeText("") }
        online
    }

    /** Nouvelle recherche en ligne (ignore le cache et l'échec précédent). */
    suspend fun refreshOnline(track: Track): Lyrics? = withContext(Dispatchers.IO) {
        val k = key(track.id)
        File(dir, "$k.none").delete()
        val online = runCatching { fetchLrclib(track) }.getOrNull()
        if (online != null) save(track, online)
        online
    }

    /** Import manuel d'un fichier .lrc / .txt choisi par l'utilisateur. */
    suspend fun import(track: Track, uri: Uri): Lyrics? = withContext(Dispatchers.IO) {
        val raw = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().take(512 * 1024).toByteArray().decodeToString() }
        }.getOrNull() ?: return@withContext null
        LrcParser.parse(raw, "Importées")?.also { save(track, it) }
    }

    fun clear(track: Track) {
        val k = key(track.id)
        File(dir, "$k.lrc").delete()
        File(dir, "$k.none").delete()
    }

    private fun save(track: Track, lyrics: Lyrics) {
        runCatching { File(dir, "${key(track.id)}.lrc").writeText(LrcParser.toText(lyrics)) }
    }

    @Suppress("DEPRECATION")
    private fun sidecarLrc(track: Track): String? {
        if (track.source != Track.Source.DEVICE) return null
        val path = runCatching {
            context.contentResolver.query(track.uri, arrayOf(MediaStore.Audio.Media.DATA), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: return null
        val base = path.substringBeforeLast('.')
        return listOf("$base.lrc", "$base.LRC").map(::File).firstOrNull { it.canRead() }
            ?.let { runCatching { it.readText() }.getOrNull() }
    }

    private fun fetchLrclib(track: Track): Lyrics? {
        var title = track.title
        var artist = track.artist.takeIf { it != Track.UNKNOWN_ARTIST }.orEmpty()
        // Fichiers nommés « Artiste - Titre » sans tags.
        if (artist.isBlank() && title.contains(" - ")) {
            artist = title.substringBefore(" - ").trim()
            title = title.substringAfter(" - ").trim()
        }
        if (artist.isNotBlank()) {
            val url = buildString {
                append("https://lrclib.net/api/get?track_name=").append(urlEncode(title))
                append("&artist_name=").append(urlEncode(artist))
                if (track.album != Track.UNKNOWN_ALBUM) append("&album_name=").append(urlEncode(track.album))
                if (track.durationMs > 0) append("&duration=").append(track.durationMs / 1000)
            }
            runCatching { fromLrclib(JSONObject(httpGet(url))) }.getOrNull()?.let { return it }
        }
        val search = "https://lrclib.net/api/search?track_name=${urlEncode(title)}" +
            if (artist.isNotBlank()) "&artist_name=${urlEncode(artist)}" else ""
        val results = JSONArray(httpGet(search))
        val candidates = (0 until results.length()).mapNotNull { results.optJSONObject(it) }
        // On préfère des paroles synchronisées dont la durée correspond (± 3 s).
        val best = candidates.sortedWith(
            compareByDescending<JSONObject> { it.optString("syncedLyrics").isNotBlank() }
                .thenBy { o ->
                    val d = o.optDouble("duration", 0.0) * 1000
                    if (track.durationMs > 0 && d > 0) kotlin.math.abs(d - track.durationMs) else 0.0
                },
        ).firstOrNull() ?: return null
        return fromLrclib(best)
    }

    private fun fromLrclib(o: JSONObject): Lyrics? {
        if (o.optBoolean("instrumental")) return Lyrics(false, listOf(LyricLine(-1, "♪ Instrumental ♪")), "LRCLIB")
        o.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
            ?.let { LrcParser.parse(it, "LRCLIB")?.let { l -> return l } }
        return o.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }?.let { LrcParser.parse(it, "LRCLIB") }
    }
}
