package com.khn.mp3.playback

import androidx.media3.common.Player

/**
 * Volume final du lecteur = baisse pour appel/notification × fondu.
 * Évite que le fondu et la gestion des appels se marchent dessus.
 */
class VolumeMixer(private val player: Player) {
    var duck: Float = 1f
        set(value) { field = value.coerceIn(0f, 1f); apply() }
    var fade: Float = 1f
        set(value) { field = value.coerceIn(0f, 1f); apply() }

    private fun apply() {
        player.volume = duck * fade
    }
}
