package com.khn.mp3.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khn.mp3.ui.MainViewModel
import com.khn.mp3.ui.components.EmptyState
import com.khn.mp3.ui.components.MenuAction
import androidx.compose.material.icons.rounded.DeleteForever
import com.khn.mp3.ui.components.SectionTitle
import com.khn.mp3.ui.components.TrackRow
import com.khn.mp3.ui.components.neonFieldColors
import com.khn.mp3.ui.theme.Khn
import com.khn.mp3.ui.theme.KhnColors

@Composable
fun SearchScreen(
    vm: MainViewModel,
    contentPadding: PaddingValues,
    onAddToPlaylist: (List<String>) -> Unit,
    onOpenPlaylist: (String) -> Unit,
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val player by vm.playerState.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) { if (query.isEmpty()) runCatching { focus.requestFocus() } }

    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        item(key = "title") {
            Text(
                "Recherche",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 12.dp),
            )
        }
        item(key = "field") {
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                singleLine = true,
                placeholder = { Text("Titre, artiste ou album") },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = KhnColors.Violet) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Rounded.Close, "Effacer") }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                shape = Khn.shapes.pill,
                colors = neonFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .focusRequester(focus),
            )
        }

        if (query.isBlank()) {
            item(key = "hint") {
                EmptyState(Icons.Rounded.Search, "Trouve ta musique", "Tape le nom d'un titre, d'un artiste, d'un album ou d'une playlist.")
            }
        } else if (results.query == query && results.tracks.isEmpty() && results.playlists.isEmpty()) {
            item(key = "none") {
                EmptyState(Icons.Rounded.SearchOff, "Aucun résultat", "Rien ne correspond à « $query » dans ta bibliothèque.")
            }
        } else {
            if (results.playlists.isNotEmpty()) {
                item(key = "pl-title") { SectionTitle("Playlists", Modifier.padding(top = 12.dp)) }
                items(results.playlists, key = { "pl-${it.id}" }) { p ->
                    ListItem(
                        headlineContent = { Text(p.name, style = MaterialTheme.typography.titleSmall) },
                        supportingContent = { Text("${p.trackIds.size} titres", color = KhnColors.TextMuted) },
                        leadingContent = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = KhnColors.Violet) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .clickable { onOpenPlaylist(p.id) }
                            .padding(horizontal = 4.dp),
                    )
                }
            }
            if (results.tracks.isNotEmpty()) {
                item(key = "tr-title") { SectionTitle("Titres · ${results.tracks.size}", Modifier.padding(top = 12.dp)) }
                itemsIndexed(results.tracks, key = { _, t -> t.id }, contentType = { _, _ -> "track" }) { index, track ->
                    TrackRow(
                        track = track,
                        isCurrent = player.currentId == track.id,
                        isPlaying = player.isPlaying,
                        isFavorite = track.id in favorites,
                        onClick = { focusManager.clearFocus(); vm.play(results.tracks, index) },
                        onToggleFavorite = { vm.toggleFavorite(track.id) },
                        onPlayNext = { vm.playNext(track) },
                        onAddToQueue = { vm.addToQueue(track) },
                        onAddToPlaylist = { onAddToPlaylist(listOf(track.id)) },
                        extraActions = listOf(
                            MenuAction("Supprimer du téléphone", Icons.Rounded.DeleteForever) { vm.askDelete(listOf(track)) },
                        ),
                    )
                }
            }
        }
    }
}
