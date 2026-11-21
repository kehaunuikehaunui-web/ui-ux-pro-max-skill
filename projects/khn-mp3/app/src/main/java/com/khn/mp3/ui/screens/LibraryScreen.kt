package com.khn.mp3.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khn.mp3.data.SortOrder
import com.khn.mp3.data.Track
import com.khn.mp3.ui.LibraryFilter
import com.khn.mp3.ui.MainViewModel
import com.khn.mp3.ui.components.EmptyState
import com.khn.mp3.ui.components.GradientButton
import com.khn.mp3.ui.components.MenuAction
import com.khn.mp3.ui.components.OutlineButton
import com.khn.mp3.ui.components.TrackRow
import com.khn.mp3.ui.theme.KhnColors

@Composable
fun LibraryScreen(
    vm: MainViewModel,
    contentPadding: PaddingValues,
    onAddToPlaylist: (List<String>) -> Unit,
    onRequestPermission: () -> Unit,
    onImportFiles: () -> Unit,
) {
    val tracks by vm.libraryTracks.collectAsStateWithLifecycle()
    val total by vm.tracks.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val player by vm.playerState.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val hasPermission by vm.hasPermission.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    PullToRefreshBox(
        isRefreshing = scanning,
        onRefresh = vm::refreshLibrary,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
            item(key = "header", contentType = "header") {
                Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "KHN MP3",
                            style = MaterialTheme.typography.displaySmall.merge(TextStyle(brush = KhnColors.Neon)),
                            modifier = Modifier.weight(1f),
                        )
                        SortMenu(settings.sort, vm::setSort)
                        IconButton(onClick = onImportFiles) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = "Ajouter des fichiers audio")
                        }
                    }
                    Text(
                        "${total.size} titres · ${favorites.size} favoris",
                        style = MaterialTheme.typography.bodyMedium,
                        color = KhnColors.TextMuted,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GradientButton(
                            "Lecture aléatoire", Icons.Rounded.Shuffle,
                            onClick = { vm.shuffle(tracks) },
                            enabled = tracks.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        )
                        OutlineButton(
                            "Tout lire", Icons.Rounded.PlayArrow,
                            onClick = { vm.play(tracks, 0) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            stickyHeader(key = "filters", contentType = "filters") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(KhnColors.Background.copy(alpha = 0.88f))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LibraryFilter.entries.forEach { f ->
                        FilterChip(
                            selected = f == filter,
                            onClick = { vm.setFilter(f) },
                            label = { Text(f.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = KhnColors.Violet,
                                selectedLabelColor = androidx.compose.ui.graphics.Color.White,
                                containerColor = KhnColors.SurfaceHigh.copy(alpha = 0.8f),
                                labelColor = KhnColors.Text,
                            ),
                            border = null,
                        )
                    }
                }
            }

            when {
                !hasPermission -> item(key = "perm") {
                    EmptyState(
                        Icons.Rounded.LockOpen,
                        "Accès à ta musique",
                        "Autorise KHN MP3 à lire les fichiers audio de ton téléphone pour remplir ta bibliothèque.",
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            GradientButton("Autoriser l'accès", Icons.Rounded.LockOpen, onRequestPermission)
                            Spacer(Modifier.height(10.dp))
                            OutlineButton("Choisir des fichiers", Icons.Rounded.FolderOpen, onImportFiles)
                        }
                    }
                }
                tracks.isEmpty() && !scanning -> item(key = "empty") {
                    EmptyState(
                        Icons.Rounded.LibraryMusic,
                        when (filter) {
                            LibraryFilter.FAVORITES -> "Aucun favori"
                            LibraryFilter.DOWNLOADED -> "Aucun téléchargement"
                            LibraryFilter.IMPORTED -> "Aucun fichier importé"
                            LibraryFilter.ALL -> "Bibliothèque vide"
                        },
                        when (filter) {
                            LibraryFilter.FAVORITES -> "Touche ♥ sur un titre pour l'ajouter ici."
                            LibraryFilter.DOWNLOADED -> "Les musiques téléchargées depuis l'onglet Télécharger apparaîtront ici."
                            else -> "Ajoute des MP3 sur ton téléphone ou choisis des fichiers dans n'importe quel dossier."
                        },
                    ) {
                        if (filter == LibraryFilter.ALL || filter == LibraryFilter.IMPORTED) {
                            OutlineButton("Choisir des fichiers", Icons.Rounded.FolderOpen, onImportFiles)
                        }
                    }
                }
                else -> itemsIndexed(tracks, key = { _, t -> t.id }, contentType = { _, _ -> "track" }) { index, track ->
                    TrackRow(
                        track = track,
                        isCurrent = player.currentId == track.id,
                        isPlaying = player.isPlaying,
                        isFavorite = track.id in favorites,
                        onClick = { vm.play(tracks, index) },
                        onToggleFavorite = { vm.toggleFavorite(track.id) },
                        onPlayNext = { vm.playNext(track) },
                        onAddToQueue = { vm.addToQueue(track) },
                        onAddToPlaylist = { onAddToPlaylist(listOf(track.id)) },
                        extraActions = if (track.source == Track.Source.IMPORTED)
                            listOf(MenuAction("Retirer de la bibliothèque", Icons.Rounded.Delete) { vm.removeImported(track) })
                        else emptyList(),
                        modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                    )
                }
            }
        }
    }
}

@Composable
private fun SortMenu(current: SortOrder, onSelect: (SortOrder) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Trier") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortOrder.entries.forEach { o ->
                DropdownMenuItem(
                    text = { Text(o.label) },
                    trailingIcon = { if (o == current) Icon(Icons.Rounded.Check, null, tint = KhnColors.Violet) },
                    onClick = { open = false; onSelect(o) },
                )
            }
        }
    }
}
