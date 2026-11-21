package com.khn.mp3.ui.screens

import android.net.Uri
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.khn.mp3.data.Track
import com.khn.mp3.data.formatDuration
import com.khn.mp3.playback.PlayerState
import com.khn.mp3.ui.components.Artwork
import com.khn.mp3.ui.components.NeonSlider
import com.khn.mp3.ui.components.PlayingBars
import com.khn.mp3.ui.theme.Khn
import com.khn.mp3.ui.theme.KhnColors
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private fun artworkUriFor(state: PlayerState, track: Track?): Uri? =
    track?.uri ?: state.currentId?.takeIf { it.startsWith("ext:") }?.let { Uri.parse(it.removePrefix("ext:")) }

/** Mini-lecteur au-dessus de la barre de navigation. */
@Composable
fun MiniPlayer(
    state: PlayerState,
    track: Track?,
    position: StateFlow<Long>,
    onOpen: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .fillMaxWidth()
            .shadow(16.dp, Khn.shapes.card, ambientColor = KhnColors.Violet, spotColor = KhnColors.Violet)
            .clip(Khn.shapes.card)
            .background(KhnColors.SurfaceHigh.copy(alpha = 0.97f))
            .clickable(onClick = onOpen),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(artworkUriFor(state, track), state.currentId.orEmpty(), Modifier.size(46.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(state.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(state.artist, style = MaterialTheme.typography.bodySmall, color = KhnColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onTogglePlay) {
                if (state.isBuffering) CircularProgressIndicator(Modifier.size(22.dp), color = KhnColors.Violet, strokeWidth = 2.dp)
                else Icon(
                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Lecture",
                    modifier = Modifier.size(30.dp),
                )
            }
            IconButton(onClick = onNext) { Icon(Icons.Rounded.SkipNext, contentDescription = "Suivant") }
        }
        // Barre de progression : la position est lue pendant le dessin uniquement (aucune recomposition).
        val pos by position.collectAsStateWithLifecycle()
        val duration = state.durationMs
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(2.dp)
                .drawBehind {
                    val f = if (duration > 0) (pos.toFloat() / duration).coerceIn(0f, 1f) else 0f
                    drawRect(KhnColors.NeonHorizontal, size = size.copy(width = size.width * f))
                },
        )
    }
}

/** Écran « Lecture en cours » plein écran, fermeture par glissement vers le bas. */
@Composable
fun NowPlayingScreen(
    state: PlayerState,
    track: Track?,
    tracksById: Map<String, Track>,
    position: StateFlow<Long>,
    isFavorite: Boolean,
    onClose: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenSound: () -> Unit,
    onSkipTo: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    var showQueue by remember { mutableStateOf(false) }
    val artUri = artworkUriFor(state, track)

    Box(
        Modifier
            .fillMaxSize()
            .offset { IntOffset(0, offsetY.value.roundToInt().coerceAtLeast(0)) }
            .background(KhnColors.Background)
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta -> scope.launch { offsetY.snapTo((offsetY.value + delta).coerceAtLeast(0f)) } },
                onDragStopped = { velocity ->
                    if (offsetY.value > 260f || velocity > 2200f) onClose()
                    else offsetY.animateTo(0f, spring())
                },
            ),
    ) {
        // Fond : pochette floutée (Android 12+) + dégradé sombre
        if (Build.VERSION.SDK_INT >= 31) {
            Artwork(
                artUri, state.currentId.orEmpty(),
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.55f }
                    .blur(70.dp),
                shape = RoundedCornerShape(0.dp),
                iconSize = 0.dp,
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to KhnColors.Background.copy(alpha = 0.35f),
                        0.55f to KhnColors.Background.copy(alpha = 0.75f),
                        1f to KhnColors.Background,
                    ),
                ),
        )

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Fermer", modifier = Modifier.size(32.dp))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("LECTURE EN COURS", style = MaterialTheme.typography.labelSmall, color = KhnColors.TextMuted)
                    Text(
                        if (state.queue.isNotEmpty()) "${state.index + 1} / ${state.queue.size}" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = KhnColors.Violet,
                    )
                }
                IconButton(onClick = onOpenSound) { Icon(Icons.Rounded.Equalizer, contentDescription = "Paramètres du son") }
            }

            Spacer(Modifier.weight(0.6f))

            // Pochette : légère respiration selon lecture / pause
            val scale by animateFloatAsState(if (state.isPlaying) 1f else 0.9f, spring(dampingRatio = 0.6f, stiffness = 300f), label = "art")
            Artwork(
                artUri, state.currentId.orEmpty(),
                Modifier
                    .fillMaxWidth(0.86f)
                    .aspectRatio(1f)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .shadow(32.dp, RoundedCornerShape(28.dp), ambientColor = KhnColors.Violet, spotColor = KhnColors.Magenta),
                shape = RoundedCornerShape(28.dp),
                iconSize = 96.dp,
            )

            Spacer(Modifier.weight(0.5f))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        state.title.ifEmpty { "—" },
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(),
                    )
                    Text(state.artist, style = MaterialTheme.typography.bodyLarge, color = KhnColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onToggleFavorite, enabled = track != null) {
                    Icon(
                        if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favori",
                        tint = if (isFavorite) KhnColors.Magenta else KhnColors.Text,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            SeekBar(position, state.durationMs, onSeek)
            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = "Aléatoire", tint = if (state.shuffle) KhnColors.Violet else KhnColors.TextMuted)
                }
                IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Précédent", modifier = Modifier.size(40.dp))
                }
                Box(
                    Modifier
                        .size(76.dp)
                        .shadow(24.dp, CircleShape, ambientColor = KhnColors.Magenta, spotColor = KhnColors.Violet)
                        .clip(CircleShape)
                        .background(KhnColors.Neon)
                        .clickable(onClick = onTogglePlay),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.isBuffering) CircularProgressIndicator(Modifier.size(32.dp), color = Color.White, strokeWidth = 3.dp)
                    else Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Lecture",
                        tint = Color.White,
                        modifier = Modifier.size(44.dp),
                    )
                }
                IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Suivant", modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = onCycleRepeat) {
                    Icon(
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = "Répéter",
                        tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) KhnColors.Violet else KhnColors.TextMuted,
                    )
                }
            }

            Spacer(Modifier.weight(0.4f))

            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onAddToPlaylist, enabled = track != null) {
                    Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = "Ajouter à une playlist")
                }
                IconButton(onClick = { showQueue = true }) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "File d'attente")
                }
            }
        }
    }

    if (showQueue) {
        QueueSheet(state, tracksById, onSkipTo, onRemoveFromQueue) { showQueue = false }
    }
}

@Composable
private fun SeekBar(position: StateFlow<Long>, durationMs: Long, onSeek: (Long) -> Unit) {
    val pos by position.collectAsStateWithLifecycle()
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val duration = durationMs.coerceAtLeast(1L)
    val shown = if (dragging) dragValue else (pos.toFloat() / duration).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth()) {
        NeonSlider(
            value = shown,
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = {
                onSeek((dragValue * duration).toLong())
                dragging = false
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration((shown * duration).toLong()), style = MaterialTheme.typography.labelMedium, color = KhnColors.TextMuted)
            Text(formatDuration(durationMs), style = MaterialTheme.typography.labelMedium, color = KhnColors.TextMuted)
        }
    }
}

@Composable
private fun QueueSheet(
    state: PlayerState,
    tracksById: Map<String, Track>,
    onSkipTo: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { if (state.index > 0) listState.scrollToItem((state.index - 1).coerceAtLeast(0)) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = KhnColors.Surface,
    ) {
        Text("File d'attente · ${state.queue.size}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 32.dp)) {
            itemsIndexed(state.queue, key = { i, e -> "$i-${e.mediaId}" }) { i, entry ->
                val current = i == state.index
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSkipTo(i) }
                        .padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Artwork(tracksById[entry.mediaId]?.uri, entry.mediaId, Modifier.size(44.dp))
                        if (current) Box(Modifier.size(44.dp).clip(Khn.shapes.artwork).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                            PlayingBars(state.isPlaying)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.title, style = MaterialTheme.typography.titleSmall, color = if (current) KhnColors.Violet else KhnColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(entry.artist, style = MaterialTheme.typography.bodySmall, color = KhnColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (!current) IconButton(onClick = { onRemove(i) }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Retirer", tint = KhnColors.TextMuted)
                    }
                }
            }
        }
    }
}
