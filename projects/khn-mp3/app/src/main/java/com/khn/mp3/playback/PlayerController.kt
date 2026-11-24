package com.khn.mp3.playback

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.khn.mp3.container
import com.khn.mp3.data.Track
import com.khn.mp3.data.coverFor
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Immutable
data class PlayerState(
    val currentId: String? = null,
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<QueueEntry> = emptyList(),
    val index: Int = -1,
) {
    val hasMedia: Boolean get() = currentId != null
}

@Immutable
data class QueueEntry(val mediaId: String, val title: String, val artist: String)

/**
 * Pont entre l'interface et [PlaybackService] via un MediaController.
 * La position est exposée séparément pour ne pas recomposer tout l'écran 4 fois par seconde.
 */
@OptIn(UnstableApi::class)
class PlayerController(private val context: Context) {

    private val scope = MainScope()
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val pending = ArrayDeque<(MediaController) -> Unit>()
    private var ticker: Job? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            val queueChanged = events.contains(Player.EVENT_TIMELINE_CHANGED)
            sync(player, rebuildQueue = queueChanged)
        }
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull() ?: run { future = null; return@addListener }
            controller = c
            c.addListener(listener)
            // Reprise : la file et la position de la dernière session, en pause.
            if (c.mediaItemCount == 0 && pending.isEmpty()) {
                context.container.snapshots.load()?.let { snap ->
                    c.setMediaItems(snap.items.map { it.toMediaItem() }, snap.index, snap.positionMs)
                    c.shuffleModeEnabled = snap.shuffle
                    c.repeatMode = snap.repeatMode
                }
            }
            sync(c, rebuildQueue = true)
            while (pending.isNotEmpty()) pending.removeFirst().invoke(c)
        }, ContextCompat.getMainExecutor(context))
    }

    fun disconnect() {
        ticker?.cancel()
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else {
            pending.addLast(block)
            connect()
        }
    }

    private fun sync(p: Player, rebuildQueue: Boolean) {
        val item = p.currentMediaItem
        val queue = if (rebuildQueue || _state.value.queue.size != p.mediaItemCount) {
            (0 until p.mediaItemCount).map { i ->
                val m = p.getMediaItemAt(i)
                QueueEntry(m.mediaId, m.mediaMetadata.title?.toString().orEmpty(), m.mediaMetadata.artist?.toString().orEmpty())
            }
        } else _state.value.queue
        _state.value = PlayerState(
            currentId = item?.mediaId,
            title = item?.mediaMetadata?.title?.toString().orEmpty(),
            artist = item?.mediaMetadata?.artist?.toString().orEmpty(),
            isPlaying = p.isPlaying,
            isBuffering = p.playbackState == Player.STATE_BUFFERING,
            durationMs = p.duration.takeIf { it > 0 } ?: 0L,
            shuffle = p.shuffleModeEnabled,
            repeatMode = p.repeatMode,
            queue = queue,
            index = p.currentMediaItemIndex,
        )
        _position.value = p.currentPosition.coerceAtLeast(0)
        if (p.isPlaying) startTicker() else ticker?.cancel()
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                controller?.let { _position.value = it.currentPosition.coerceAtLeast(0) }
                delay(250)
            }
        }
    }

    // ---------------- Commandes ----------------

    fun play(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        withController { c ->
            c.shuffleModeEnabled = shuffle
            val start = if (shuffle && startIndex == 0) tracks.indices.random() else startIndex
            c.setMediaItems(tracks.map { it.toMediaItem() }, start.coerceIn(tracks.indices), 0L)
            c.prepare()
            c.play()
        }
    }

    /** Lecture d'un flux distant (extrait Internet Archive). */
    fun playStream(url: String, title: String, artist: String) {
        val item = MediaItem.Builder()
            .setMediaId("url:$url")
            .setUri(url)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse(url)).build())
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).build())
            .build()
        withController { c ->
            c.setMediaItem(item)
            c.prepare()
            c.play()
        }
    }

    fun playNext(track: Track) = withController { c ->
        if (c.mediaItemCount == 0) play(listOf(track)) else c.addMediaItem(c.currentMediaItemIndex + 1, track.toMediaItem())
    }

    fun addToQueue(track: Track) = withController { c ->
        if (c.mediaItemCount == 0) play(listOf(track)) else c.addMediaItem(track.toMediaItem())
    }

    fun togglePlay() = withController { c ->
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() = withController { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
    fun previous() = withController { it.seekToPrevious() }
    fun seekTo(ms: Long) = withController { it.seekTo(ms); _position.value = ms }
    fun skipTo(index: Int) = withController { if (index in 0 until it.mediaItemCount) it.seekToDefaultPosition(index) }
    fun removeFromQueue(index: Int) = withController { if (index in 0 until it.mediaItemCount) it.removeMediaItem(index) }
    /** Retire de la file d'attente les titres supprimés du téléphone. */
    fun removeIds(ids: Set<String>) = withController { c ->
        for (i in c.mediaItemCount - 1 downTo 0) {
            if (c.getMediaItemAt(i).mediaId in ids) c.removeMediaItem(i)
        }
    }

    /** Glisser-déposer dans la file d'attente. */
    fun moveItem(from: Int, to: Int) = withController { c ->
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount && from != to) c.moveMediaItem(from, to)
    }

    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** Élément du lecteur avec la pochette personnalisée éventuelle (affichée dans la notification). */
    private fun Track.toMediaItem(): MediaItem =
        toMediaItem(context.container.userData.data.value.covers.coverFor(this))
}
