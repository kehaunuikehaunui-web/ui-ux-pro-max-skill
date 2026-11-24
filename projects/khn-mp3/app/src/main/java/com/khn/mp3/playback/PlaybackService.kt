package com.khn.mp3.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.khn.mp3.MainActivity
import com.khn.mp3.R
import com.khn.mp3.container
import com.khn.mp3.data.PlaybackSnapshot
import com.khn.mp3.data.coverFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.random.Random

/**
 * Service de lecture : la musique continue quand l'app est fermée ou l'écran éteint.
 * Notification + contrôles écran de verrouillage / casque Bluetooth fournis par Media3.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private var exo: ExoPlayer? = null
    private var focus: CallAwareAudioFocus? = null
    private var fades: FadeEngine? = null
    private val scope = MainScope()
    private var saveJob: Job? = null

    private var smartShuffle = true
    private var autoplay = true
    private var lastRecordedId: String? = null
    private var autoplayDoneFor: String? = null
    private var lastItemCount = 0
    private var lastFirstId: String? = null

    override fun onCreate() {
        super.onCreate()
        val c = container
        val effects = c.audioEffects
        val settings = c.settings

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                // Focus géré par CallAwareAudioFocus : la musique continue pendant les appels.
                /* handleAudioFocus = */ false,
            )
            .setHandleAudioBecomingNoisy(true) // pause quand le casque est débranché
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        exo = player

        val mixer = VolumeMixer(player)
        val fadeEngine = FadeEngine(player, mixer, scope).also { fades = it }
        val callFocus = CallAwareAudioFocus(this, player, mixer).also { focus = it }

        effects.attach(player.audioSessionId)
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) = effects.attach(audioSessionId)

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                if (shuffleModeEnabled) applySmartShuffle()
                scheduleSave()
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                    val count = player.mediaItemCount
                    val firstId = if (count > 0) player.getMediaItemAt(0).mediaId else null
                    // Nouvelle liste (pas un simple ajout « Lire ensuite ») : ordre aléatoire refait, lecture auto réarmée.
                    val newList = kotlin.math.abs(count - lastItemCount) > 1 || firstId != lastFirstId
                    lastItemCount = count
                    lastFirstId = firstId
                    if (newList) {
                        autoplayDoneFor = null
                        if (player.shuffleModeEnabled) applySmartShuffle()
                    }
                    maybeAutoplay()
                    scheduleSave()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (player.isPlaying) recordPlay()
                maybeAutoplay()
                scheduleSave()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) recordPlay() else scheduleSave()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                    c.sleepTimer.cancel() // « fin du morceau » atteinte
                }
            }
        })

        scope.launch {
            settings.settings
                .map { it.sound.continueInCalls to it.sound.callVolume }
                .distinctUntilChanged()
                .collect { (continueInCalls, callVolume) ->
                    callFocus.continueInCalls = continueInCalls
                    callFocus.callVolume = callVolume
                }
        }
        scope.launch {
            settings.settings
                .map { it.sound.speed to it.sound.pitchSemitones }
                .distinctUntilChanged()
                .collect { (speed, semitones) ->
                    player.playbackParameters = PlaybackParameters(speed, 2f.pow(semitones / 12f))
                }
        }
        scope.launch {
            settings.settings.map { it.playback }.distinctUntilChanged().collect { p ->
                fadeEngine.crossfadeMs = p.crossfadeSec * 1000L
                fadeEngine.pauseFadeMs = if (p.fadeOnPause) FadeEngine.PAUSE_FADE_MS else 0L
                smartShuffle = p.smartShuffle
                autoplay = p.autoplay
            }
        }
        // Minuterie de mise en veille.
        scope.launch {
            c.sleepTimer.state.collectLatest { state ->
                player.pauseAtEndOfMediaItems = state is SleepTimer.State.EndOfTrack
                if (state is SleepTimer.State.At) {
                    delay((state.endAtMs - System.currentTimeMillis()).coerceAtLeast(0))
                    fadeEngine.pause({ player.pause() }, durationMs = 4_000)
                    c.sleepTimer.cancel()
                }
            }
        }
        // Sauvegarde régulière de la position pendant la lecture (reprise au redémarrage).
        scope.launch {
            while (isActive) {
                delay(15_000)
                if (player.isPlaying) scheduleSave()
            }
        }

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_PLAYER)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaSession.Builder(this, FadingPlayer(player, fadeEngine))
            .setSessionActivity(openApp)
            .setCallback(SessionCallback())
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_notification)
            },
        )
    }

    /** Aléatoire intelligent : évite les titres récents et le même artiste deux fois de suite. */
    private fun applySmartShuffle() {
        val player = exo ?: return
        val n = player.mediaItemCount
        if (!smartShuffle || n < 3) return
        val items = (0 until n).map { player.getMediaItemAt(it) }
        val order = SmartQueue.shuffleOrder(items, player.currentMediaItemIndex, container.userData.data.value.recent)
        if (order.size == n) player.setShuffleOrder(DefaultShuffleOrder(order, Random.nextLong()))
    }

    /** Fin de file : ajoute des titres proches (lecture automatique). */
    private fun maybeAutoplay() {
        val player = exo ?: return
        if (!autoplay || player.repeatMode != Player.REPEAT_MODE_OFF || player.mediaItemCount == 0) return
        if (player.hasNextMediaItem()) return
        val current = player.currentMediaItem ?: return
        if (current.mediaId == autoplayDoneFor) return
        if (!current.mediaId.startsWith("ms:") && !current.mediaId.startsWith("doc:")) return
        autoplayDoneFor = current.mediaId
        val c = container
        val library = c.library.tracks.value
        val data = c.userData.data.value
        val inQueue = (0 until player.mediaItemCount).mapTo(HashSet()) { player.getMediaItemAt(it).mediaId }
        val picks = SmartQueue.suggestions(
            library = library,
            exclude = inQueue,
            seed = c.library.byId.value[current.mediaId],
            recent = data.recent,
            playCounts = data.playCounts,
        )
        if (picks.isNotEmpty()) player.addMediaItems(picks.map { it.toMediaItem(data.covers.coverFor(it)) })
    }

    /** Historique d'écoute (aléatoire intelligent, suggestions). */
    private fun recordPlay() {
        val id = exo?.currentMediaItem?.mediaId ?: return
        if (id == lastRecordedId) return
        lastRecordedId = id
        if (id.startsWith("ms:") || id.startsWith("doc:")) container.userData.recordPlay(id)
    }

    private fun snapshot(): PlaybackSnapshot? {
        val player = exo ?: return null
        if (player.mediaItemCount == 0) return null
        val items = (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).toSavedItem() }
            .filterNot { it.id.startsWith("url:") }
        if (items.isEmpty()) return null
        val currentId = player.currentMediaItem?.mediaId
        val index = items.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        return PlaybackSnapshot(
            items = items.take(1000),
            index = index.coerceAtMost(999),
            positionMs = if (items.getOrNull(index)?.id == currentId) player.currentPosition.coerceAtLeast(0) else 0,
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
        )
    }

    /** Sauvegarde différée (regroupe les événements rapprochés). */
    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(800)
            val snap = snapshot() ?: return@launch
            launch(Dispatchers.IO) { container.snapshots.save(snap) }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** L'utilisateur a balayé l'app des « récents » : on continue si une musique est en cours. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        snapshot()?.let { container.snapshots.save(it) }
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_ENDED
        ) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        snapshot()?.let { container.snapshots.save(it) }
        scope.cancel()
        fades?.release()
        focus?.release()
        focus = null
        container.audioEffects.release()
        session?.release()
        exo?.release()
        exo = null
        session = null
        super.onDestroy()
    }

    private inner class SessionCallback : MediaSession.Callback {
        /** Les MediaItem reçus du contrôleur perdent parfois leur URI : on la reconstruit. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
            mediaItems.map { item ->
                val uri = item.localConfiguration?.uri
                    ?: item.requestMetadata.mediaUri
                    ?: Uri.parse(item.mediaId.substringAfter(':'))
                item.buildUpon().setUri(uri).build()
            }.toMutableList(),
        )

        /** Bouton « lecture » du casque / de l'écran de verrouillage sans file : reprise de la dernière session. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val snap = container.snapshots.load()
                ?: return Futures.immediateFailedFuture(UnsupportedOperationException("Aucune session"))
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(snap.items.map { it.toMediaItem() }, snap.index, snap.positionMs),
            )
        }
    }
}
