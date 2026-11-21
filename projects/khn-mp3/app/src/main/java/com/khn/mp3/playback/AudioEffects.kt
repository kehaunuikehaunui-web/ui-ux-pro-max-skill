@file:Suppress("DEPRECATION") // Virtualizer : remplacé par Spatializer (API 32+) mais toujours fonctionnel

package com.khn.mp3.playback

import android.media.audiofx.BassBoost
import androidx.annotation.RequiresApi
import android.media.audiofx.DynamicsProcessing
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
import android.os.Build
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
 * Applique égaliseur, renfort de basses, spatialisation, amplification et anti-saturation
 * à la session audio du lecteur. Les réglages sont relus en continu depuis [SettingsRepository].
 *
 * Chaîne : Égaliseur → Basses → Spatialisation → [Amplification + Limiteur] (DynamicsProcessing, Android 9+).
 * Le limiteur est placé en dernier pour rattraper toute saturation créée par les effets précédents.
 * Sans DynamicsProcessing (Android 8 ou appareil incompatible), l'amplification passe par LoudnessEnhancer.
 */
class AudioEffects(private val settings: SettingsRepository, private val scope: CoroutineScope) {

    private val _spec = MutableStateFlow(EqSpec.DEFAULT)
    val spec: StateFlow<EqSpec> = _spec.asStateFlow()

    private var sessionId = 0
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var virt: Virtualizer? = null
    private var loud: LoudnessEnhancer? = null
    private var dynamics: DynamicsProcessing? = null
    private var job: Job? = null

    private val _limiterAvailable = MutableStateFlow(Build.VERSION.SDK_INT >= 28)
    /** Vrai si l'anti-saturation (limiteur) fonctionne sur cet appareil. */
    val limiterAvailable: StateFlow<Boolean> = _limiterAvailable.asStateFlow()

    fun attach(audioSessionId: Int) {
        if (audioSessionId == 0 || audioSessionId == sessionId) return
        release()
        sessionId = audioSessionId
        eq = runCatching { Equalizer(0, audioSessionId) }.getOrNull()
        bass = runCatching { BassBoost(0, audioSessionId) }.getOrNull()
        virt = runCatching { Virtualizer(0, audioSessionId) }.getOrNull()
        // Créé en dernier : le limiteur doit être le dernier maillon de la chaîne.
        dynamics = if (Build.VERSION.SDK_INT >= 28) Limiter28.create(audioSessionId) else null
        _limiterAvailable.value = dynamics != null
        if (dynamics == null) loud = runCatching { LoudnessEnhancer(audioSessionId) }.getOrNull()

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
        if (Build.VERSION.SDK_INT >= 28) dynamics?.let { d -> runCatching { Limiter28.apply(d, s) } }
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
        listOfNotNull(eq, bass, virt, loud, dynamics).forEach { runCatching { it.release() } }
        eq = null; bass = null; virt = null; loud = null; dynamics = null
        sessionId = 0
    }
}

/** Amplification + limiteur anti-saturation via DynamicsProcessing (Android 9+). */
@RequiresApi(28)
private object Limiter28 {
    fun create(sessionId: Int): DynamicsProcessing? = runCatching {
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_TIME_RESOLUTION,
            2,         // stéréo
            false, 0,  // pré-égaliseur : non (l'Equalizer s'en charge)
            false, 0,  // compresseur multibande : non
            false, 0,  // post-égaliseur : non
            true,      // limiteur : oui
        ).build()
        DynamicsProcessing(0, sessionId, config)
    }.getOrNull()

    fun apply(d: DynamicsProcessing, s: SoundSettings) {
        // Limiteur : seuil -1 dBFS, ratio 10:1, attaque 1 ms, relâchement 100 ms.
        d.setLimiterAllChannelsTo(
            DynamicsProcessing.Limiter(true, s.antiSaturation, 0, 1f, 100f, 10f, -1f, 0f),
        )
        d.setInputGainAllChannelsTo(s.loudnessGain / 100f)
        d.enabled = s.loudnessGain > 0 || s.antiSaturation
    }
}
