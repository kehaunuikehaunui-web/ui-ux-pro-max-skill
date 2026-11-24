package com.khn.mp3.playback

import androidx.media3.common.MediaItem
import com.khn.mp3.data.Track
import kotlin.math.ln
import kotlin.random.Random

/** File d'attente intelligente : aléatoire sans répétitions et suggestions de lecture automatique. */
object SmartQueue {

    private fun artistOf(item: MediaItem): String? =
        item.mediaMetadata.artist?.toString()?.lowercase()?.takeIf { it.isNotBlank() && it != Track.UNKNOWN_ARTIST.lowercase() }

    /**
     * Ordre aléatoire :
     * - le titre en cours reste en tête ;
     * - les titres écoutés récemment passent à la fin ;
     * - on évite deux titres du même artiste à la suite.
     */
    fun shuffleOrder(items: List<MediaItem>, current: Int, recent: List<String>, random: Random = Random.Default): IntArray {
        val recentRank = recent.take(80).withIndex().associate { it.value to it.index }
        val others = items.indices.filter { it != current }.shuffled(random)
        val (fresh, played) = others.partition { items[it].mediaId !in recentRank }
        // Parmi les titres récents, les moins récents d'abord.
        val pool = (fresh + played.sortedByDescending { recentRank[items[it].mediaId] }).toMutableList()

        val result = ArrayList<Int>(items.size)
        var lastArtist: String? = null
        if (current in items.indices) {
            result += current
            lastArtist = artistOf(items[current])
        }
        while (pool.isNotEmpty()) {
            val look = minOf(pool.size, 12)
            val pick = (0 until look).firstOrNull { i ->
                val a = artistOf(items[pool[i]])
                a == null || a != lastArtist
            } ?: 0
            val idx = pool.removeAt(pick)
            result += idx
            lastArtist = artistOf(items[idx])
        }
        return result.toIntArray()
    }

    /** Titres proches du dernier écouté : même artiste, même album, tes plus écoutés, un peu de découverte. */
    fun suggestions(
        library: List<Track>,
        exclude: Set<String>,
        seed: Track?,
        recent: List<String>,
        playCounts: Map<String, Int>,
        max: Int = 10,
        random: Random = Random.Default,
    ): List<Track> {
        val recentSet = recent.take(40).toSet()
        val candidates = library.filter { it.id !in exclude && it.id !in recentSet }
            .ifEmpty { library.filter { it.id !in exclude } }
        if (candidates.isEmpty()) return emptyList()
        fun score(t: Track): Double {
            var s = random.nextDouble() * 1.5
            if (seed != null) {
                if (t.artist == seed.artist && t.artist != Track.UNKNOWN_ARTIST) s += 3.0
                if (t.album == seed.album && t.album != Track.UNKNOWN_ALBUM) s += 2.0
            }
            s += ln(1.0 + (playCounts[t.id] ?: 0))
            return s
        }
        return candidates.sortedByDescending(::score).take(max)
    }
}
