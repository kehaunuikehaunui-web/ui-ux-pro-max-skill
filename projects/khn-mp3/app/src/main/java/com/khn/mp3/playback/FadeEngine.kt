package com.khn.mp3.playback

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Transitions :
 * - fondu de sortie sur les dernières secondes d'un morceau, puis fondu d'entrée du suivant ;
 * - fondu court à la pause et à la reprise.
 */
class FadeEngine(
    private val exo: ExoPlayer,
    private val mixer: VolumeMixer,
    private val scope: CoroutineScope,
) {
    /** Durée du fondu entre morceaux (0 = désactivé). */
    var crossfadeMs: Long = 0
    /** Durée du fondu à la pause / reprise (0 = désactivé). */
    var pauseFadeMs: Long = PAUSE_FADE_MS

    private var rampJob: Job? = null
    private var tickJob: Job? = null
    private var fadingOut = false

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) startTicker() else tickJob?.cancel()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val automatic = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
            if (automatic && crossfadeMs > 0 && fadingOut) {
                ramp(0f, 1f, (crossfadeMs / 2).coerceAtLeast(500))
            } else if (rampJob?.isActive != true) {
                mixer.fade = 1f
            }
            fadingOut = false
        }
    }

    init {
        exo.addListener(listener)
    }

    private fun startTicker() {
        if (tickJob?.isActive == true) return
        tickJob = scope.launch {
            while (isActive) {
                val cf = crossfadeMs
                if (cf > 0 && rampJob?.isActive != true) {
                    val duration = exo.duration
                    if (duration != C.TIME_UNSET && duration > cf * 2) {
                        val remaining = duration - exo.currentPosition
                        if (remaining in 0..cf) {
                            fadingOut = true
                            mixer.fade = remaining.toFloat() / cf
                        } else if (fadingOut) {
                            // Retour en arrière pendant le fondu : volume normal.
                            fadingOut = false
                            mixer.fade = 1f
                        }
                    }
                }
                delay(if (cf > 0) 50 else 500)
            }
        }
    }

    private fun ramp(from: Float, to: Float, durationMs: Long, then: (() -> Unit)? = null) {
        rampJob?.cancel()
        rampJob = scope.launch {
            val start = SystemClock.uptimeMillis()
            mixer.fade = from
            while (isActive) {
                val t = (SystemClock.uptimeMillis() - start).toFloat() / durationMs
                if (t >= 1f) break
                mixer.fade = from + (to - from) * t
                delay(16)
            }
            mixer.fade = to
            then?.invoke()
        }
    }

    /** Reprise : on part du silence et on monte le volume. */
    fun play(doPlay: () -> Unit) {
        rampJob?.cancel()
        if (pauseFadeMs <= 0 || exo.isPlaying) {
            mixer.fade = 1f
            doPlay()
            return
        }
        mixer.fade = 0f
        doPlay()
        ramp(0f, 1f, pauseFadeMs)
    }

    /** Pause : on baisse le volume puis on met en pause. */
    fun pause(doPause: () -> Unit, durationMs: Long = pauseFadeMs) {
        if (durationMs <= 0 || !exo.isPlaying) {
            rampJob?.cancel()
            doPause()
            mixer.fade = 1f
            return
        }
        ramp(mixer.fade, 0f, durationMs) {
            doPause()
            mixer.fade = 1f
        }
    }

    fun release() {
        rampJob?.cancel()
        tickJob?.cancel()
        exo.removeListener(listener)
    }

    companion object {
        const val PAUSE_FADE_MS = 300L
    }
}

/** Lecteur exposé à la session : applique les fondus aux commandes lecture/pause (notification, casque, app). */
@OptIn(UnstableApi::class)
class FadingPlayer(private val exo: ExoPlayer, private val fades: FadeEngine) : ForwardingPlayer(exo) {
    override fun play() = fades.play { exo.play() }
    override fun pause() = fades.pause({ exo.pause() })
    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady) play() else pause()
    }
}
