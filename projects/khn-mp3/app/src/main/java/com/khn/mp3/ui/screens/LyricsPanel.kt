package com.khn.mp3.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khn.mp3.data.Lyrics
import com.khn.mp3.data.Track
import com.khn.mp3.ui.MainViewModel
import com.khn.mp3.ui.theme.KhnColors
import kotlinx.coroutines.flow.StateFlow

/** Paroles du morceau en cours : karaoké synchronisé (ligne active centrée, toucher pour y aller) ou texte simple. */
@Composable
fun LyricsPanel(
    vm: MainViewModel,
    track: Track?,
    position: StateFlow<Long>,
    accent: Color,
    onSeek: (Long) -> Unit,
    onImport: () -> Unit,
) {
    val ui by vm.lyrics.collectAsStateWithLifecycle()
    LaunchedEffect(track?.id) { vm.loadLyrics(track) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            track == null -> Message("Les paroles sont disponibles pour les titres de ta bibliothèque.")
            ui is MainViewModel.LyricsUi.Loading || (ui as? MainViewModel.LyricsUi.Ready)?.trackId != track.id ->
                CircularProgressIndicator(color = accent)
            else -> {
                val lyrics = (ui as MainViewModel.LyricsUi.Ready).lyrics
                if (lyrics == null || lyrics.isEmpty) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Message("Aucune parole trouvée pour ce titre.")
                        Spacer(Modifier.height(12.dp))
                        LyricsActions(accent, onSearch = { vm.searchLyricsOnline(track) }, onImport = onImport)
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f)) {
                            if (lyrics.synced) SyncedLyrics(lyrics, position, onSeek) else PlainLyrics(lyrics)
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Paroles : ${lyrics.source}",
                                style = MaterialTheme.typography.labelSmall,
                                color = KhnColors.TextMuted,
                                modifier = Modifier.weight(1f),
                            )
                            LyricsActions(accent, onSearch = { vm.searchLyricsOnline(track) }, onImport = onImport, compact = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncedLyrics(lyrics: Lyrics, position: StateFlow<Long>, onSeek: (Long) -> Unit) {
    val pos by position.collectAsStateWithLifecycle()
    // Ligne active : ne provoque une recomposition que lorsqu'elle change.
    val current by remember(lyrics) {
        derivedStateOf { lyrics.lines.indexOfLast { it.timeMs <= pos + 250 } }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(current) {
        if (current >= 0 && !listState.isScrollInProgress) {
            val viewport = listState.layoutInfo.viewportSize.height
            listState.animateScrollToItem(current, scrollOffset = -(viewport / 3))
        }
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(vertical = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(lyrics.lines, key = { i, _ -> i }) { i, line ->
            val active = i == current
            val color by animateColorAsState(
                when {
                    active -> Color.White
                    i < current -> KhnColors.TextMuted.copy(alpha = 0.55f)
                    else -> KhnColors.TextMuted.copy(alpha = 0.8f)
                },
                tween(250),
                label = "lyricColor",
            )
            Text(
                line.text.ifBlank { "♪" },
                style = if (active) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                color = color,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSeek(line.timeMs) }
                    .padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun PlainLyrics(lyrics: Lyrics) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(lyrics.lines, key = { i, _ -> i }) { _, line ->
            Text(line.text, style = MaterialTheme.typography.titleMedium, color = KhnColors.Text)
        }
    }
}

@Composable
private fun LyricsActions(accent: Color, onSearch: () -> Unit, onImport: () -> Unit, compact: Boolean = false) {
    Row {
        TextButton(onClick = onSearch) {
            Icon(Icons.Rounded.CloudDownload, null, tint = accent, modifier = Modifier.size(18.dp))
            if (!compact) Text("  Rechercher en ligne", color = accent)
        }
        TextButton(onClick = onImport) {
            Icon(Icons.Rounded.FileOpen, null, tint = accent, modifier = Modifier.size(18.dp))
            if (!compact) Text("  Importer un .lrc", color = accent)
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = KhnColors.TextMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
}
