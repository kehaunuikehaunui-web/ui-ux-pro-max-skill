package com.khn.mp3.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player

/**
 * Focus audio « compatible appels » (remplace la gestion automatique d'ExoPlayer, qui met en pause).
 *
 * - Appel vidéo / vocal (WhatsApp, Messenger, Meet, téléphone…) : la musique continue à [callVolume]
 *   si [continueInCalls] est activé, sinon pause puis reprise automatique à la fin de l'appel.
 * - Notification ou annonce GPS : baisse temporaire du volume.
 * - Une autre app de musique démarre : pause (comportement standard d'Android).
 *
 * KHN MP3 ne demande jamais le focus pendant un appel, pour ne pas perturber l'application d'appel.
 */
class CallAwareAudioFocus(
    context: Context,
    private val player: Player,
    private val mixer: VolumeMixer,
) {

    private val am = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    var continueInCalls = true
    var callVolume = DEFAULT_CALL_VOLUME
        set(value) {
            field = value
            if (duckedForCall) mixer.duck = value
        }

    private var hasFocus = false
    private var pausedByFocus = false
    private var duckedForCall = false
    private var expectedPlayWhenReady: Boolean? = null

    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        // On gère nous-mêmes la baisse de volume (niveau réglable) au lieu du « ducking » système.
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener(::onFocusChange, handler)
        .build()

    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (expectedPlayWhenReady == playWhenReady) {
                expectedPlayWhenReady = null // changement fait par nous : ignorer
                return
            }
            pausedByFocus = false
            if (playWhenReady) ensureFocus() else abandon()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) abandon()
        }
    }

    private val callWatcher = object : Runnable {
        override fun run() {
            if (isInCall()) {
                handler.postDelayed(this, CALL_POLL_MS)
                return
            }
            // Appel terminé : volume normal et reprise du focus si la musique doit jouer.
            unduck()
            if (pausedByFocus) {
                pausedByFocus = false
                setPlayWhenReady(true)
            }
            if (player.playWhenReady) ensureFocus()
        }
    }

    init {
        player.addListener(listener)
    }

    private fun isInCall(): Boolean {
        val mode = am.mode
        if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION ||
            mode == AudioManager.MODE_RINGTONE
        ) return true
        if (Build.VERSION.SDK_INT >= 30 && mode == AudioManager.MODE_CALL_SCREENING) return true
        if (Build.VERSION.SDK_INT >= 33 &&
            (mode == AudioManager.MODE_CALL_REDIRECT || mode == AudioManager.MODE_COMMUNICATION_REDIRECT)
        ) return true
        return false
    }

    private fun ensureFocus() {
        if (hasFocus) {
            if (!duckedForCall) unduck()
            return
        }
        if (isInCall()) {
            // Lecture lancée pendant un appel : on joue à volume réduit, sans voler le focus à l'appel.
            duckForCall()
            return
        }
        hasFocus = am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        unduck()
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true
                handler.removeCallbacks(callWatcher)
                unduck()
                if (pausedByFocus) {
                    pausedByFocus = false
                    setPlayWhenReady(true)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> mixer.duck = DUCK_VOLUME
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (continueInCalls) duckForCall(watch = false) // le retour du focus signalera la fin
                else pauseByFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasFocus = false
                when {
                    isInCall() && continueInCalls -> duckForCall()
                    isInCall() -> { pauseByFocus(); handler.postDelayed(callWatcher, CALL_POLL_MS) }
                    else -> {
                        // Une autre app de musique a pris la main : pause définitive.
                        pausedByFocus = false
                        setPlayWhenReady(false)
                        am.abandonAudioFocusRequest(request)
                    }
                }
            }
        }
    }

    private fun pauseByFocus() {
        if (!player.playWhenReady) return
        pausedByFocus = true
        setPlayWhenReady(false)
    }

    private fun duckForCall(watch: Boolean = true) {
        duckedForCall = true
        mixer.duck = callVolume
        if (watch) {
            handler.removeCallbacks(callWatcher)
            handler.postDelayed(callWatcher, CALL_POLL_MS)
        }
    }

    private fun unduck() {
        duckedForCall = false
        mixer.duck = 1f
    }

    private fun setPlayWhenReady(value: Boolean) {
        if (player.playWhenReady == value) return
        expectedPlayWhenReady = value
        player.playWhenReady = value
    }

    private fun abandon() {
        handler.removeCallbacks(callWatcher)
        if (hasFocus) am.abandonAudioFocusRequest(request)
        hasFocus = false
        unduck()
    }

    fun release() {
        player.removeListener(listener)
        abandon()
    }

    private companion object {
        const val DUCK_VOLUME = 0.3f
        const val DEFAULT_CALL_VOLUME = 0.3f
        const val CALL_POLL_MS = 1500L
    }
}
