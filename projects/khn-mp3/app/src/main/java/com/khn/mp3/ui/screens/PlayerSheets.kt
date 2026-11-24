package com.khn.mp3.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.TimerOff
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.khn.mp3.data.Track
import com.khn.mp3.data.coverKeyAlbum
import com.khn.mp3.data.coverKeyTrack
import com.khn.mp3.playback.PlayerState
import com.khn.mp3.playback.QueueEntry
import com.khn.mp3.playback.SleepTimer
import com.khn.mp3.ui.components.LocalCovers
import com.khn.mp3.ui.components.PlayingBars
import com.khn.mp3.ui.components.TrackArtwork
import com.khn.mp3.ui.theme.Khn
import com.khn.mp3.ui.theme.KhnColors

private data class QueueRow(val key: String, val entry: QueueEntry, val originalIndex: Int)

/** Clés stables (un même titre peut apparaître plusieurs fois dans la file). */
private fun buildRows(queue: List<QueueEntry>): List<QueueRow> {
    val seen = HashMap<String, Int>()
    return queue.mapIndexed { i, e ->
        val n = (seen[e.mediaId] ?: 0) + 1
        seen[e.mediaId] = n
        QueueRow("${e.mediaId}#$n", e, i)
    }
}

/** File d'attente : toucher pour lire, ✕ pour retirer, poignée ≡ pour déplacer. */
@Composable
fun QueueSheet(
    state: PlayerState,
    tracksById: Map<String, Track>,
    onSkipTo: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
    var rows by remember(state.queue) { mutableStateOf(buildRows(state.queue)) }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragFrom by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) { if (state.index > 0) listState.scrollToItem((state.index - 1).coerceAtLeast(0)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = KhnColors.Surface,
    ) {
        Text(
            "File d'attente · ${state.queue.size}",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Text(
            "Maintiens la poignée ≡ et fais glisser pour réorganiser.",
            style = MaterialTheme.typography.bodySmall,
            color = KhnColors.TextMuted,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 32.dp)) {
            itemsIndexed(rows, key = { _, r -> r.key }) { _, row ->
                val current = row.originalIndex == state.index
                val dragging = row.key == draggingKey
                Row(
                    Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (dragging) dragOffset else 0f
                            shadowElevation = if (dragging) 16f else 0f
                        }
                        .then(if (dragging) Modifier else Modifier.animateItem())
                        .fillMaxWidth()
                        .background(if (dragging) KhnColors.SurfaceHigh else Color.Transparent)
                        .clickable { onSkipTo(row.originalIndex) }
                        .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.DragHandle,
                        contentDescription = "Déplacer",
                        tint = KhnColors.TextMuted,
                        modifier = Modifier
                            .size(44.dp)
                            .padding(10.dp)
                            .pointerInput(row.key) {
                                detectDragGestures(
                                    onDragStart = {
                                        draggingKey = row.key
                                        dragFrom = rows.indexOfFirst { it.key == row.key }
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                        val key = draggingKey ?: return@detectDragGestures
                                        val visible = listState.layoutInfo.visibleItemsInfo
                                        val self = visible.firstOrNull { it.key == key } ?: return@detectDragGestures
                                        val center = self.offset + self.size / 2 + dragOffset
                                        val target = visible.firstOrNull {
                                            it.key != key && center.toInt() in it.offset..(it.offset + it.size)
                                        } ?: return@detectDragGestures
                                        val from = rows.indexOfFirst { it.key == key }
                                        val to = rows.indexOfFirst { it.key == target.key }
                                        if (from < 0 || to < 0) return@detectDragGestures
                                        rows = rows.toMutableList().apply { add(to, removeAt(from)) }
                                        dragOffset += (self.offset - target.offset)
                                    },
                                    onDragEnd = {
                                        val to = rows.indexOfFirst { it.key == draggingKey }
                                        if (dragFrom >= 0 && to >= 0 && dragFrom != to) onMove(dragFrom, to)
                                        draggingKey = null
                                        dragOffset = 0f
                                    },
                                    onDragCancel = {
                                        draggingKey = null
                                        dragOffset = 0f
                                        rows = buildRows(state.queue)
                                    },
                                )
                            },
                    )
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        TrackArtwork(tracksById[row.entry.mediaId], Modifier.size(44.dp), seed = row.entry.mediaId)
                        if (current) {
                            Box(
                                Modifier.size(44.dp).clip(Khn.shapes.artwork).background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center,
                            ) { PlayingBars(state.isPlaying) }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            row.entry.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (current) KhnColors.Violet else KhnColors.Text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(row.entry.artist, style = MaterialTheme.typography.bodySmall, color = KhnColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (!current) IconButton(onClick = { onRemove(row.originalIndex) }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Retirer", tint = KhnColors.TextMuted)
                    }
                }
            }
        }
    }
}

/** Minuterie de mise en veille. */
@Composable
fun SleepTimerSheet(
    state: SleepTimer.State,
    onStart: (Int) -> Unit,
    onEndOfTrack: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = KhnColors.Surface,
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Minuterie de mise en veille", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            Text(
                "La musique baisse doucement puis s'arrête.",
                style = MaterialTheme.typography.bodySmall,
                color = KhnColors.TextMuted,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            listOf(5, 15, 30, 45, 60, 90).forEach { m ->
                SheetOption(Icons.Rounded.Bedtime, "$m minutes") { onStart(m) }
            }
            SheetOption(Icons.Rounded.Album, "À la fin du morceau", selected = state is SleepTimer.State.EndOfTrack) { onEndOfTrack() }
            if (state !is SleepTimer.State.Off) {
                SheetOption(Icons.Rounded.TimerOff, "Désactiver la minuterie", tint = KhnColors.Error) { onCancel() }
            }
        }
    }
}

/** Choix de la pochette d'un titre ou de son album. */
@Composable
fun CoverSheet(
    track: Track,
    onPick: (String) -> Unit,
    onReset: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val covers = LocalCovers.current
    val trackKey = coverKeyTrack(track.id)
    val albumKey = coverKeyAlbum(track)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = KhnColors.Surface,
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TrackArtwork(track, Modifier.size(56.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Pochette", style = MaterialTheme.typography.titleLarge)
                    Text(track.title, style = MaterialTheme.typography.bodySmall, color = KhnColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            SheetOption(Icons.Rounded.Image, "Choisir une image pour ce titre") { onPick(trackKey) }
            if (albumKey != null) {
                SheetOption(Icons.Rounded.Album, "Choisir une image pour l'album « ${track.album} »") { onPick(albumKey) }
            }
            if (covers.containsKey(trackKey)) {
                SheetOption(Icons.Rounded.Restore, "Rétablir la pochette d'origine du titre") { onReset(trackKey) }
            }
            if (albumKey != null && covers.containsKey(albumKey)) {
                SheetOption(Icons.Rounded.Restore, "Rétablir la pochette d'origine de l'album") { onReset(albumKey) }
            }
        }
    }
}

@Composable
private fun SheetOption(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    tint: Color = KhnColors.Violet,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint)
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) KhnColors.Violet else KhnColors.Text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
