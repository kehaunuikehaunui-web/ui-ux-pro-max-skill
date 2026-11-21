@file:Suppress("DEPRECATION") // Virtualizer : remplacé par Spatializer (API 32+) mais toujours fonctionnel

package com.khn.mp3.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import androidx.compose.runtime.Immutable
import com.khn.mp3.data.SettingsRepository
import com.khn.mp3.data.SoundSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ln

/** Description de l'égaliseur matériel de l'appareil (nombre de bandes, plage). */
@Immutable
data class EqSpec(
    val centerHz: List<Int>,
    val minLevel: Int,
    val maxLevel: Int,
    val available: Boolean,
) {
    companion object {
        val DEFAULT = EqSpec(listOf(60, 230, 910, 3600, 14000), -1500, 1500, available = false)
    }
}

/** Préréglages en dB aux fréquences 60 / 230 / 910 / 3,6k / 14k Hz, adaptés aux bandes réelles de l'appareil. */
object EqPresets {
    private val refHz = listOf(60, 230, 910, 3600, 14000)
    val all: List<Pair<String, List<Int>>> = listOf(
        "Normal" to listOf(0, 0, 0, 0, 0),
        "Basses+" to listOf(6, 4, 0, 0, 1),
        "Pop" to listOf(-1, 2, 4, 2, -1),
        "Rock" to listOf(5, 3, -1, 3, 5),
        "Hip-Hop" to listOf(5, 4, 0, 1, 3),
        "Électro" to listOf(4, 2, 0, 2, 5),
        "Jazz" to listOf(3, 1, -1, 2, 4),
        "Classique" to listOf(4, 2, -2, 2, 4),
        "Voix" to listOf(-2, 0, 3, 3, 1),
        "Aigus+" to listOf(0, 0, 1, 4, 6),
    )

    fun levelsFor(name: String, spec: EqSpec): List<Int> {
        val db = all.firstOrNull { it.first == name }?.second ?: return List(spec.centerHz.size) { 0 }
        return spec.centerHz.map { hz ->
            val nearest = refHz.indices.minBy { abs(ln(refHz[it].toDouble()) - ln(hz.toDouble())) }
            (db[nearest] * 100).coerceIn(spec.minLevel, spec.maxLevel)
        }
    }
}

/**
 * Applique égaliseur, renfort de basses, spatialisation et amplification à la session audio du lecteur.
 * Les réglages sont relus en continu depuis [SettingsRepository].
 */
class AudioEffects(private val settings: SettingsRepository, private val scope: CoroutineScope) {

    private val _spec = MutableStateFlow(EqSpec.DEFAULT)
    val spec: StateFlow<EqSpec> = _spec.asStateFlow()

    private var sessionId = 0
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var virt: Virtualizer? = null
    private var loud: LoudnessEnhancer? = null
    private var job: Job? = null

    fun attach(audioSessionId: Int) {
        if (audioSessionId == 0 || audioSessionId == sessionId) return
        release()
        sessionId = audioSessionId
        eq = runCatching { Equalizer(0, audioSessionId) }.getOrNull()
        bass = runCatching { BassBoost(0, audioSessionId) }.getOrNull()
        virt = runCatching { Virtualizer(0, audioSessionId) }.getOrNull()
        loud = runCatching { LoudnessEnhancer(audioSessionId) }.getOrNull()

        eq?.let { e ->
            runCatching {
                val range = e.bandLevelRange
                _spec.value = EqSpec(
                    centerHz = (0 until e.numberOfBands).map { e.getCenterFreq(it.toShort()) / 1000 },
                    minLevel = range[0].toInt(),
                    maxLevel = range[1].toInt(),
                    available = true,
                )
            }
        }

        job = scope.launch(Dispatchers.Main) {
            settings.settings.map { it.sound }.distinctUntilChanged().collect { apply(it) }
        }
    }

    /** Aperçu en direct pendant qu'un curseur est déplacé (sans écriture disque). */
    fun preview(s: SoundSettings) = apply(s)

    private fun apply(s: SoundSettings) {
        val spec = _spec.value
        eq?.let { e ->
            runCatching {
                val levels = if (s.bandLevels.size == spec.centerHz.size) s.bandLevels
                else EqPresets.levelsFor(s.preset, spec)
                levels.forEachIndexed { i, level ->
                    e.setBandLevel(i.toShort(), level.coerceIn(spec.minLevel, spec.maxLevel).toShort())
                }
                e.enabled = s.eqEnabled
            }
        }
        bass?.let { b ->
            runCatching {
                if (b.strengthSupported) b.setStrength(s.bassBoost.toShort())
                b.enabled = s.bassBoost > 0
            }
        }
        virt?.let { v ->
            runCatching {
                if (v.strengthSupported) v.setStrength(s.virtualizer.toShort())
                v.enabled = s.virtualizer > 0
            }
        }
        loud?.let { l ->
            runCatching {
                l.setTargetGain(s.loudnessGain)
                l.enabled = s.loudnessGain > 0
            }
        }
    }

    fun release() {
        job?.cancel()
        job = null
        listOfNotNull(eq, bass, virt, loud).forEach { runCatching { it.release() } }
        eq = null; bass = null; virt = null; loud = null
        sessionId = 0
    }
}
