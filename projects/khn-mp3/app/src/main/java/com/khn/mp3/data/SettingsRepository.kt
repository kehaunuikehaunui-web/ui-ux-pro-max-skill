package com.khn.mp3.data

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore(name = "settings")

@Immutable
data class SoundSettings(
    val eqEnabled: Boolean = false,
    val bandLevels: List<Int> = emptyList(), // millibels, une valeur par bande de l'égaliseur
    val preset: String = "Normal",
    val bassBoost: Int = 0,                  // 0..1000
    val virtualizer: Int = 0,                // 0..1000
    val loudnessGain: Int = 0,               // millibels 0..1500 (= 0..+15 dB)
    val speed: Float = 1f,
    val pitchSemitones: Int = 0,
    val antiSaturation: Boolean = true,      // limiteur : le son reste propre une fois amplifié
    val continueInCalls: Boolean = true,     // la musique continue pendant les appels
    val callVolume: Float = 0.3f,            // volume de la musique pendant un appel (0.1..1)
)

@Immutable
data class Settings(
    val sound: SoundSettings = SoundSettings(),
    val backgroundPath: String? = null,
    val backgroundDim: Float = 0.55f,
    val backgroundBlur: Int = 6,
    val minDurationSec: Int = 30,
    val sort: SortOrder = SortOrder.TITLE,
    val jamendoClientId: String = "",
)

/** Amplification maximale : +15 dB. */
const val MAX_BOOST_MB = 1500

class SettingsRepository(private val context: Context, private val scope: CoroutineScope) {

    private object K {
        val eqEnabled = booleanPreferencesKey("eq_enabled")
        val bands = stringPreferencesKey("eq_bands")
        val preset = stringPreferencesKey("eq_preset")
        val bass = intPreferencesKey("bass_boost")
        val virt = intPreferencesKey("virtualizer")
        val loud = intPreferencesKey("loudness")
        val speed = floatPreferencesKey("speed")
        val pitch = intPreferencesKey("pitch_semitones")
        val antiSat = booleanPreferencesKey("anti_saturation")
        val calls = booleanPreferencesKey("continue_in_calls")
        val callVol = floatPreferencesKey("call_volume")
        val bgPath = stringPreferencesKey("bg_path")
        val bgDim = floatPreferencesKey("bg_dim")
        val bgBlur = intPreferencesKey("bg_blur")
        val minDur = intPreferencesKey("min_duration")
        val sort = stringPreferencesKey("sort")
        val jamendo = stringPreferencesKey("jamendo_client_id")
    }

    val settings: StateFlow<Settings> = context.dataStore.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, Settings())

    private fun Preferences.toSettings(): Settings {
        val d = Settings()
        return Settings(
            sound = SoundSettings(
                eqEnabled = this[K.eqEnabled] ?: d.sound.eqEnabled,
                bandLevels = this[K.bands]?.split(',')?.mapNotNull { it.toIntOrNull() } ?: emptyList(),
                preset = this[K.preset] ?: d.sound.preset,
                bassBoost = this[K.bass] ?: 0,
                virtualizer = this[K.virt] ?: 0,
                loudnessGain = this[K.loud] ?: 0,
                speed = this[K.speed] ?: 1f,
                pitchSemitones = this[K.pitch] ?: 0,
                antiSaturation = this[K.antiSat] ?: d.sound.antiSaturation,
                continueInCalls = this[K.calls] ?: d.sound.continueInCalls,
                callVolume = this[K.callVol] ?: d.sound.callVolume,
            ),
            backgroundPath = this[K.bgPath],
            backgroundDim = this[K.bgDim] ?: d.backgroundDim,
            backgroundBlur = this[K.bgBlur] ?: d.backgroundBlur,
            minDurationSec = this[K.minDur] ?: d.minDurationSec,
            sort = this[K.sort]?.let { runCatching { SortOrder.valueOf(it) }.getOrNull() } ?: d.sort,
            jamendoClientId = this[K.jamendo] ?: "",
        )
    }

    private fun edit(block: (MutablePreferences) -> Unit) {
        scope.launch { context.dataStore.edit { block(it) } }
    }

    fun setEqEnabled(v: Boolean) = edit { it[K.eqEnabled] = v }
    fun setBands(levels: List<Int>, preset: String) = edit {
        it[K.bands] = levels.joinToString(",")
        it[K.preset] = preset
    }
    fun setBassBoost(v: Int) = edit { it[K.bass] = v.coerceIn(0, 1000) }
    fun setVirtualizer(v: Int) = edit { it[K.virt] = v.coerceIn(0, 1000) }
    fun setLoudness(v: Int) = edit { it[K.loud] = v.coerceIn(0, MAX_BOOST_MB) }
    fun setAntiSaturation(v: Boolean) = edit { it[K.antiSat] = v }
    fun setContinueInCalls(v: Boolean) = edit { it[K.calls] = v }
    fun setCallVolume(v: Float) = edit { it[K.callVol] = v.coerceIn(0.1f, 1f) }
    fun setSpeed(v: Float) = edit { it[K.speed] = v.coerceIn(0.5f, 2f) }
    fun setPitch(semitones: Int) = edit { it[K.pitch] = semitones.coerceIn(-6, 6) }
    fun resetSound() = edit {
        listOf(K.eqEnabled, K.bands, K.preset, K.bass, K.virt, K.loud, K.speed, K.pitch, K.antiSat).forEach { k -> it.remove(k) }
    }

    fun setBackground(path: String?) = edit { if (path == null) it.remove(K.bgPath) else it[K.bgPath] = path }
    fun setBackgroundDim(v: Float) = edit { it[K.bgDim] = v.coerceIn(0f, 0.95f) }
    fun setBackgroundBlur(v: Int) = edit { it[K.bgBlur] = v.coerceIn(0, 25) }
    fun setMinDuration(sec: Int) = edit { it[K.minDur] = sec.coerceIn(0, 180) }
    fun setSort(order: SortOrder) = edit { it[K.sort] = order.name }
    fun setJamendoClientId(v: String) = edit { it[K.jamendo] = v.trim() }
}
