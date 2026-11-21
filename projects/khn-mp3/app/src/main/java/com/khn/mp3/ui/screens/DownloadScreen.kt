package com.khn.mp3.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khn.mp3.data.ArchiveRepository
import com.khn.mp3.data.DownloadRepository
import com.khn.mp3.data.formatDuration
import com.khn.mp3.ui.MainViewModel
import com.khn.mp3.ui.components.GradientButton
import com.khn.mp3.ui.components.SectionTitle
import com.khn.mp3.ui.components.neonFieldColors
import com.khn.mp3.ui.theme.Khn
import com.khn.mp3.ui.theme.KhnColors

@Composable
fun DownloadScreen(vm: MainViewModel, contentPadding: PaddingValues) {
    val archive by vm.archive.collectAsStateWithLifecycle()
    val statuses by vm.downloadStatuses.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var url by rememberSaveable { mutableStateOf("") }

    // Android 8/9 : l'écriture dans Musique/ demande une permission.
    val pendingDownload = remember { mutableStateOf<(() -> Unit)?>(null) }
    val writePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingDownload.value?.invoke()
        pendingDownload.value = null
    }
    fun withWriteAccess(action: () -> Unit) {
        val needs = Build.VERSION.SDK_INT <= 28 && ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) != PackageManager.PERMISSION_GRANTED
        if (needs) {
            pendingDownload.value = action
            writePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else action()
    }

    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        item(key = "title") {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp)) {
                Text("Télécharger", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Les fichiers sont enregistrés dans Musique/KHN MP3 et apparaissent dans ta bibliothèque.",
                    style = MaterialTheme.typography.bodySmall,
                    color = KhnColors.TextMuted,
                )
            }
        }

        // ---- Depuis un lien ----
        item(key = "url") {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Link, null, tint = KhnColors.Violet)
                    Spacer(Modifier.width(10.dp))
                    Text("Depuis un lien direct", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it.trim() },
                    singleLine = true,
                    placeholder = { Text("https://…/musique.mp3") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        focusManager.clearFocus()
                        withWriteAccess { if (vm.download(url)) url = "" }
                    }),
                    colors = neonFieldColors(),
                    shape = Khn.shapes.card,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                GradientButton(
                    "Télécharger", Icons.Rounded.Download,
                    onClick = {
                        focusManager.clearFocus()
                        withWriteAccess { if (vm.download(url)) url = "" }
                    },
                    enabled = url.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // ---- Téléchargements en cours / récents ----
        if (statuses.isNotEmpty()) {
            item(key = "dl-title") {
                Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Mes téléchargements", Modifier.weight(1f))
                    TextButton(onClick = vm::clearDownloads) { Text("Effacer la liste", color = KhnColors.TextMuted) }
                }
            }
            items(statuses, key = { "dl-${it.id}" }) { s -> DownloadRow(s, onCancel = { vm.cancelDownload(s.id) }) }
        }

        // ---- Internet Archive ----
        item(key = "ia") {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Public, null, tint = KhnColors.Cyan)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Musique libre", style = MaterialTheme.typography.titleMedium)
                        Text("Catalogue Internet Archive · Creative Commons & domaine public", style = MaterialTheme.typography.bodySmall, color = KhnColors.TextMuted)
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = archive.query,
                    onValueChange = vm::setArchiveQuery,
                    singleLine = true,
                    placeholder = { Text("Artiste, genre, ambiance…") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = KhnColors.Violet) },
                    trailingIcon = {
                        if (archive.loading) CircularProgressIndicator(Modifier.size(20.dp), color = KhnColors.Violet, strokeWidth = 2.dp)
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus(); vm.searchArchive() }),
                    colors = neonFieldColors(),
                    shape = Khn.shapes.pill,
                    modifier = Modifier.fillMaxWidth(),
                )
                archive.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = KhnColors.Error, style = MaterialTheme.typography.bodySmall)
                }
                if (archive.searched && !archive.loading && archive.error == null && archive.items.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Aucun résultat. Essaie un autre mot-clé (ex. « jazz », « lofi », « piano »).", color = KhnColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        items(archive.items, key = { "ia-${it.identifier}" }) { item ->
            val expanded = archive.expanded == item.identifier
            ArchiveItemRow(
                item = item,
                expanded = expanded,
                files = archive.files[item.identifier],
                onToggle = { vm.toggleArchiveItem(item) },
                onPreview = vm::previewStream,
                onDownload = { f -> withWriteAccess { vm.download(f.url, f.title, f.fileName) } },
            )
        }

        item(key = "legal") {
            Text(
                "Télécharge uniquement des musiques que tu as le droit d'utiliser. Les liens YouTube, Spotify ou Deezer ne sont pas pris en charge.",
                style = MaterialTheme.typography.bodySmall,
                color = KhnColors.TextMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(Khn.shapes.card)
            .background(KhnColors.Surface.copy(alpha = 0.82f))
            .padding(16.dp),
    ) { content() }
}

@Composable
private fun DownloadRow(s: DownloadRepository.Status, onCancel: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (s.state) {
            DownloadRepository.State.DONE -> Icon(Icons.Rounded.CheckCircle, null, tint = KhnColors.Cyan)
            DownloadRepository.State.FAILED -> Icon(Icons.Rounded.ErrorOutline, null, tint = KhnColors.Error)
            else -> CircularProgressIndicator(Modifier.size(22.dp), color = KhnColors.Violet, strokeWidth = 2.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(s.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            when (s.state) {
                DownloadRepository.State.RUNNING, DownloadRepository.State.PENDING, DownloadRepository.State.PAUSED -> {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { s.progress },
                        color = KhnColors.Violet,
                        trackColor = KhnColors.Outline,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                DownloadRepository.State.DONE -> Text("Terminé · dans ta bibliothèque", style = MaterialTheme.typography.bodySmall, color = KhnColors.TextMuted)
                DownloadRepository.State.FAILED -> Text("Échec du téléchargement", style = MaterialTheme.typography.bodySmall, color = KhnColors.Error)
            }
        }
        if (s.state == DownloadRepository.State.RUNNING || s.state == DownloadRepository.State.PENDING || s.state == DownloadRepository.State.PAUSED) {
            IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, "Annuler", tint = KhnColors.TextMuted) }
        }
    }
}

@Composable
private fun ArchiveItemRow(
    item: ArchiveRepository.Item,
    expanded: Boolean,
    files: com.khn.mp3.ui.FilesState?,
    onToggle: () -> Unit,
    onPreview: (ArchiveRepository.AudioFile) -> Unit,
    onDownload: (ArchiveRepository.AudioFile) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(Khn.shapes.card)
            .background(KhnColors.Surface.copy(alpha = if (expanded) 0.9f else 0.6f)),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Album, null, tint = KhnColors.Violet, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(item.creator, style = MaterialTheme.typography.bodySmall, color = KhnColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = KhnColors.TextMuted)
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(bottom = 8.dp)) {
                when {
                    files == null || files.loading -> Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = KhnColors.Violet, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Chargement des pistes…", color = KhnColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    files.error != null -> Text(files.error, color = KhnColors.TextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
                    else -> files.files.take(60).forEach { f ->
                        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onPreview(f) }) { Icon(Icons.Rounded.PlayCircle, "Écouter un extrait", tint = KhnColors.Cyan) }
                            Column(Modifier.weight(1f)) {
                                Text(f.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(
                                        f.durationMs.takeIf { it > 0 }?.let { formatDuration(it) },
                                        f.sizeBytes.takeIf { it > 0 }?.let { "%.1f Mo".format(it / 1_048_576.0) },
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = KhnColors.TextMuted,
                                )
                            }
                            IconButton(onClick = { onDownload(f) }) { Icon(Icons.Rounded.Download, "Télécharger", tint = KhnColors.Violet) }
                        }
                    }
                }
            }
        }
    }
}
