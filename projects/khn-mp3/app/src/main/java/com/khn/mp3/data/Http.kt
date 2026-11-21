package com.khn.mp3.data

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal const val USER_AGENT = "KHN-MP3/1.2 (Android)"

/** GET simple (à appeler hors du thread UI). Lève une exception si le code HTTP n'est pas 2xx. */
internal fun httpGet(url: String): String {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 15_000
    conn.readTimeout = 20_000
    conn.setRequestProperty("User-Agent", USER_AGENT)
    conn.setRequestProperty("Accept", "application/json")
    try {
        if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
        return conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
        conn.disconnect()
    }
}

internal fun urlEncode(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

/** « 4:13 », « 1:02:03 » ou « 253.4 » (secondes) → millisecondes. */
internal fun parseClock(raw: String?): Long {
    if (raw.isNullOrBlank()) return 0L
    raw.trim().toDoubleOrNull()?.let { return (it * 1000).toLong() }
    val parts = raw.trim().split(':').mapNotNull { it.trim().toDoubleOrNull() }
    if (parts.isEmpty()) return 0L
    return (parts.fold(0.0) { acc, v -> acc * 60 + v } * 1000).toLong()
}

private val YOUTUBE_HOSTS = Regex("""(^|\.)(youtube\.com|youtu\.be|youtube-nocookie\.com)$""", RegexOption.IGNORE_CASE)

/** Vrai pour un lien YouTube / YouTube Music (non téléchargeable). */
internal fun isYoutubeUrl(url: String): Boolean =
    runCatching { URL(url.trim()).host }.getOrNull()?.let { YOUTUBE_HOSTS.containsMatchIn(it) } ?: false
