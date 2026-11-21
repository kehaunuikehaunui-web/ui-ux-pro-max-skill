package com.khn.mp3.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khn.mp3.data.TrashEntry
import com.khn.mp3.ui.MainViewModel
import com.khn.mp3.ui.components.Artwork
import com.khn.mp3.ui.components.EmptyState
import com.khn.mp3.ui.components.GradientButton
import com.khn.mp3.ui.components.OutlineButton
import com.khn.mp3.ui.theme.Khn
import com.khn.mp3.ui.theme.KhnColors
import java.io.File
import java.util.concurrent.TimeUnit

@Composable
fun TrashScreen(vm: MainViewModel, contentPadding: PaddingValues, onBack: () -> Unit) {
    val entries by vm.trash.collectAsStateWithLifecycle()

    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        item(key = "top") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
                Column(Modifier.weight(1f)) {
                    Text("Corbeille", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (entries.size == 1) "1 musique" else "${entries.size} musiques",
                        style = MaterialTheme.typography.bodySmall,
                        color = KhnColors.TextMuted,
                    )
                }
            }
        }
        item(key = "info") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(Khn.shapes.card)
                    .background(KhnColors.Surface.copy(alpha = 0.82f))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Info, null, tint = KhnColors.Violet)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Les musiques supprimées restent ici 30 jours, puis sont effacées définitivement. " +
                        "Une musique restaurée retrouve ses favoris et ses playlists.",
                    style = MaterialTheme.typography.bodySmall,
                    color = KhnColors.Text,
                )
            }
        }
        if (entries.isEmpty()) {
            item(key = "empty") {
                EmptyState(Icons.Rounded.Delete, "Corbeille vide", "Les musiques que tu supprimes arrivent ici.")
            }
        } else {
            item(key = "actions") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GradientButton("Tout restaurer", Icons.Rounded.RestoreFromTrash, { vm.restoreFromTrash(entries) }, Modifier.weight(1f))
                    OutlineButton("Vider", Icons.Rounded.DeleteForever, { vm.askDeleteForever(entries) }, Modifier.weight(1f))
                }
            }
            items(entries, key = { it.id }) { e ->
                TrashRow(
                    entry = e,
                    onRestore = { vm.restoreFromTrash(listOf(e)) },
                    onDelete = { vm.askDeleteForever(listOf(e)) },
                )
            }
        }
    }
}

@Composable
private fun TrashRow(entry: TrashEntry, onRestore: () -> Unit, onDelete: () -> Unit) {
    val daysLeft = TimeUnit.MILLISECONDS.toDays(entry.expiresAt - System.currentTimeMillis()).coerceAtLeast(0)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Pochette lisible uniquement pour les copies de la corbeille interne.
        Artwork(entry.file?.let { Uri.fromFile(File(it)) }, entry.originalId, Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${entry.artist} · ${if (daysLeft <= 1) "effacée demain" else "encore $daysLeft jours"}",
                style = MaterialTheme.typography.bodySmall,
                color = if (daysLeft <= 3) KhnColors.Magenta else KhnColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onRestore) { Icon(Icons.Rounded.RestoreFromTrash, "Restaurer", tint = KhnColors.Violet) }
        IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteForever, "Supprimer définitivement", tint = KhnColors.TextMuted) }
    }
}
