package com.khn.mp3.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khn.mp3.data.FAVORITES_PLAYLIST_ID
import com.khn.mp3.data.Playlist
import com.khn.mp3.data.Track
import com.khn.mp3.data.formatDuration
import com.khn.mp3.data.normalize
import com.khn.mp3.ui.MainViewModel
import com.khn.mp3.ui.components.Artwork
import com.khn.mp3.ui.components.EmptyState
import com.khn.mp3.ui.components.GradientButton
import com.khn.mp3.ui.components.MenuAction
import com.khn.mp3.ui.components.OutlineButton
import com.khn.mp3.ui.components.TextInputDialog
import com.khn.mp3.ui.components.TrackRow
import com.khn.mp3.ui.components.neonFieldColors
import com.khn.mp3.ui.theme.Khn
import com.khn.mp3.ui.theme.KhnColors

@Composable
fun PlaylistsScreen(
    vm: MainViewModel,
    contentPadding: PaddingValues,
    onOpen: (String) -> Unit,
) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val byId by vm.tracksById.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        item(key = "title") {
            Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Playlists", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            }
        }
        item(key = "new") {
            GradientButton(
                "Nouvelle playlist", Icons.Rounded.Add, { creating = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item(key = "fav") {
            PlaylistCard(
                name = "Favoris",
                count = favorites.size,
                cover = null,
                coverSeed = "favorites",
                highlight = true,
                onClick = { onOpen(FAVORITES_PLAYLIST_ID) },
            )
        }
        if (playlists.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    Icons.AutoMirrored.Rounded.QueueMusic,
                    "Aucune playlist",
                    "Crée une playlist pour regrouper tes musiques préférées.",
                )
            }
        }
        items(playlists, key = { it.id }) { p ->
            val first = p.trackIds.firstNotNullOfOrNull { byId[it] }
            PlaylistCard(
                name = p.name,
                count = p.trackIds.size,
                cover = first,
                coverSeed = p.id,
                highlight = false,
                onClick = { onOpen(p.id) },
            )
        }
    }

    if (creating) {
        TextInputDialog(
            title = "Nouvelle playlist",
            initial = "",
            confirmLabel = "Créer",
            onConfirm = { name -> creating = false; onOpen(vm.createPlaylist(name)) },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun PlaylistCard(
    name: String,
    count: Int,
    cover: Track?,
    coverSeed: String,
    highlight: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(Khn.shapes.card)
            .background(KhnColors.Surface.copy(alpha = 0.78f))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (highlight) {
            Box(
                Modifier.size(64.dp).clip(Khn.shapes.artwork).background(KhnColors.Neon),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Favorite, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
        } else {
            Artwork(cover?.uri, coverSeed, Modifier.size(64.dp), iconSize = 28.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$count titre${if (count > 1) "s" else ""}", style = MaterialTheme.typography.bodySmall, color = KhnColors.TextMuted)
        }
    }
}

@Composable
fun PlaylistDetailScreen(
    vm: MainViewModel,
    playlistId: String,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onAddToPlaylist: (List<String>) -> Unit,
) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val userFavOrder by vm.favoritesOrdered.collectAsStateWithLifecycle()
    val byId by vm.tracksById.collectAsStateWithLifecycle()
    val player by vm.playerState.collectAsStateWithLifecycle()

    val isFavorites = playlistId == FAVORITES_PLAYLIST_ID
    val playlist: Playlist? = playlists.firstOrNull { it.id == playlistId }
    if (!isFavorites && playlist == null) {
        // Playlist supprimée entre-temps : retour à la liste.
        LaunchedEffect(playlistId) { onBack() }
        return
    }
    val name = if (isFavorites) "Favoris" else playlist!!.name
    val ids = if (isFavorites) userFavOrder else playlist!!.trackIds
    val tracks = remember(ids, byId) { ids.mapNotNull { byId[it] } }
    val totalMs = remember(tracks) { tracks.sumOf { it.durationMs } }

    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        item(key = "top") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
                Spacer(Modifier.weight(1f))
                if (!isFavorites) Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Renommer") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; renaming = true })
                        DropdownMenuItem(text = { Text("Supprimer") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; confirmDelete = true })
                    }
                }
            }
        }
        item(key = "header") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (isFavorites) {
                    Box(Modifier.size(180.dp).clip(Khn.shapes.card).background(KhnColors.Neon), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Favorite, null, tint = Color.White, modifier = Modifier.size(80.dp))
                    }
                } else {
                    Artwork(tracks.firstOrNull()?.uri, playlistId, Modifier.size(180.dp), shape = Khn.shapes.card, iconSize = 72.dp)
                }
                Spacer(Modifier.height(16.dp))
                Text(name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${tracks.size} titre${if (tracks.size > 1) "s" else ""} · ${formatDuration(totalMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = KhnColors.TextMuted,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GradientButton("Lecture", Icons.Rounded.PlayArrow, { vm.play(tracks, 0) }, enabled = tracks.isNotEmpty(), modifier = Modifier.weight(1f))
                    OutlineButton("Aléatoire", Icons.Rounded.Shuffle, { vm.shuffle(tracks) }, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                OutlineButton("Ajouter des titres", Icons.Rounded.Add, { picking = true }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }
        }
        if (tracks.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    if (isFavorites) Icons.Rounded.Favorite else Icons.AutoMirrored.Rounded.QueueMusic,
                    "Playlist vide",
                    if (isFavorites) "Touche ♥ sur un titre pour l'ajouter à tes favoris." else "Ajoute des titres avec le bouton ci-dessus ou depuis le menu ⋮ d'un titre.",
                )
            }
        }
        itemsIndexed(tracks, key = { _, t -> t.id }, contentType = { _, _ -> "track" }) { index, track ->
            val extra = buildList {
                add(MenuAction(if (isFavorites) "Retirer des favoris" else "Retirer de la playlist", Icons.Rounded.RemoveCircleOutline) {
                    vm.removeFromPlaylist(playlistId, track.id)
                })
                if (!isFavorites && index > 0) add(MenuAction("Monter", Icons.Rounded.ArrowUpward) { vm.movePlaylistItem(playlistId, index, index - 1) })
                if (!isFavorites && index < tracks.lastIndex) add(MenuAction("Descendre", Icons.Rounded.ArrowDownward) { vm.movePlaylistItem(playlistId, index, index + 1) })
                add(MenuAction("Supprimer du téléphone", Icons.Rounded.DeleteForever) { vm.askDelete(listOf(track)) })
            }
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
                extraActions = extra,
                modifier = Modifier.animateItem(),
            )
        }
    }

    if (renaming && playlist != null) {
        TextInputDialog("Renommer", playlist.name, "Enregistrer", { vm.renamePlaylist(playlistId, it); renaming = false }, { renaming = false })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = KhnColors.SurfaceHigh,
            title = { Text("Supprimer « $name » ?") },
            text = { Text("Les musiques restent sur ton téléphone, seule la playlist est supprimée.", color = KhnColors.TextMuted) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deletePlaylist(playlistId); onBack() }) { Text("Supprimer", color = KhnColors.Error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler", color = KhnColors.TextMuted) } },
        )
    }
    if (picking) {
        TrackPickerSheet(
            vm = vm,
            alreadyIn = ids.toSet(),
            onConfirm = { picked -> picking = false; if (picked.isNotEmpty()) vm.addToPlaylist(playlistId, picked) },
            onDismiss = { picking = false },
        )
    }
}

/** Sélection multiple de titres de la bibliothèque, avec recherche. */
@Composable
private fun TrackPickerSheet(
    vm: MainViewModel,
    alreadyIn: Set<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val all by vm.tracks.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }
    val shown = remember(all, filter, alreadyIn) {
        val tokens = normalize(filter).split(' ').filter { it.isNotBlank() }
        all.filter { it.id !in alreadyIn && tokens.all { tk -> tk in it.searchKey } }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = KhnColors.Surface,
    ) {
        Column(Modifier.fillMaxHeight(0.92f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Ajouter des titres", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { onConfirm(selected.toList()) }, enabled = selected.isNotEmpty()) {
                    Text("Ajouter (${selected.size})", color = if (selected.isNotEmpty()) KhnColors.Violet else KhnColors.TextMuted)
                }
            }
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                singleLine = true,
                placeholder = { Text("Filtrer") },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = KhnColors.Violet) },
                shape = Khn.shapes.pill,
                colors = neonFieldColors(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.weight(1f).navigationBarsPadding()) {
                items(shown, key = { it.id }, contentType = { "pick" }) { t ->
                    val checked = t.id in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { if (checked) selected.remove(t.id) else selected.add(t.id) }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(t.uri, t.id, Modifier.size(44.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t.artist, style = MaterialTheme.typography.bodySmall, color = KhnColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { if (it) selected.add(t.id) else selected.remove(t.id) },
                            colors = CheckboxDefaults.colors(checkedColor = KhnColors.Violet),
                        )
                    }
                }
            }
        }
    }
}
