package com.khn.mp3.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.khn.mp3.data.FAVORITES_PLAYLIST_ID
import com.khn.mp3.data.Playlist
import com.khn.mp3.data.Track
import com.khn.mp3.data.formatDuration
import com.khn.mp3.ui.theme.Khn
import com.khn.mp3.ui.theme.KhnColors

/** Entrée supplémentaire du menu « ⋮ » d'un titre. */
data class MenuAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

@Composable
fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
    extraActions: List<MenuAction> = emptyList(),
    leading: (@Composable () -> Unit)? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .background(if (selected) KhnColors.Violet.copy(alpha = 0.16f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Box(Modifier.size(52.dp)) {
            Artwork(track.uri, track.id, Modifier.size(52.dp))
            if (isCurrent) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(Khn.shapes.artwork)
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) { PlayingBars(isPlaying) }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isCurrent) KhnColors.Violet else KhnColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${track.artist} · ${formatDuration(track.durationMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = KhnColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selectionMode) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onClick() },
                colors = CheckboxDefaults.colors(checkedColor = KhnColors.Violet),
                modifier = Modifier.padding(end = 8.dp),
            )
            return@Row
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (isFavorite) "Retirer des favoris" else "Ajouter aux favoris",
                tint = if (isFavorite) KhnColors.Magenta else KhnColors.TextMuted,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Options", tint = KhnColors.TextMuted)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                MenuItem("Lire ensuite", Icons.Rounded.SkipNext) { menu = false; onPlayNext() }
                MenuItem("Ajouter à la file d'attente", Icons.AutoMirrored.Rounded.QueueMusic) { menu = false; onAddToQueue() }
                MenuItem("Ajouter à une playlist", Icons.AutoMirrored.Rounded.PlaylistAdd) { menu = false; onAddToPlaylist() }
                extraActions.forEach { a -> MenuItem(a.label, a.icon) { menu = false; a.onClick() } }
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(icon, null, tint = KhnColors.Violet) },
        onClick = onClick,
    )
}

/** Confirmation avant suppression définitive. */
@Composable
fun DeleteConfirmDialog(tracks: List<Track>, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val n = tracks.size
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KhnColors.SurfaceHigh,
        icon = { Icon(Icons.Rounded.DeleteForever, null, tint = KhnColors.Error) },
        title = { Text(if (n == 1) "Supprimer cette musique ?" else "Supprimer $n musiques ?") },
        text = {
            Column {
                Text(
                    if (n == 1) "« ${tracks.first().title} » sera effacée définitivement du téléphone."
                    else "Ces $n musiques seront effacées définitivement du téléphone.",
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Cette action est irréversible." +
                        if (android.os.Build.VERSION.SDK_INT >= 29) " Android va ensuite te demander de confirmer." else "",
                    color = KhnColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Supprimer", color = KhnColors.Error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler", color = KhnColors.TextMuted) } },
    )
}

/** Petit égaliseur animé sur le titre en cours (animation limitée à cette seule ligne). */
@Composable
fun PlayingBars(playing: Boolean, color: Color = KhnColors.Text) {
    val transition = rememberInfiniteTransition(label = "bars")
    val phases = listOf(0, 180, 360).map { delay ->
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(520, delayMillis = delay), RepeatMode.Reverse),
            label = "bar$delay",
        )
    }
    Box(
        Modifier
            .size(20.dp)
            .drawBehind {
                val barW = size.width / 5
                phases.forEachIndexed { i, p ->
                    val h = size.height * (if (playing) p.value else 0.3f)
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(i * barW * 2, size.height - h),
                        size = Size(barW, h),
                        cornerRadius = CornerRadius(barW / 2),
                    )
                }
            },
    )
}

@Composable
fun GradientButton(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(Khn.shapes.pill)
            .background(if (enabled) KhnColors.NeonHorizontal else androidx.compose.ui.graphics.SolidColor(KhnColors.SurfaceHigh))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

@Composable
fun OutlineButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(Khn.shapes.pill)
            .background(KhnColors.SurfaceHigh.copy(alpha = 0.85f))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = KhnColors.Violet, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = KhnColors.Text)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = KhnColors.Violet,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(KhnColors.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = KhnColors.Violet, modifier = Modifier.size(34.dp)) }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = KhnColors.Text)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = KhnColors.TextMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    placeholder: String = "Nom de la playlist",
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KhnColors.SurfaceHigh,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(60) },
                singleLine = true,
                placeholder = { Text(placeholder) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onConfirm(text) }),
                colors = neonFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text(confirmLabel, color = if (text.isNotBlank()) KhnColors.Violet else KhnColors.TextMuted)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler", color = KhnColors.TextMuted) } },
    )
}

@Composable
fun neonFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = KhnColors.Violet,
    unfocusedBorderColor = KhnColors.Outline,
    cursorColor = KhnColors.Violet,
    focusedContainerColor = KhnColors.Surface.copy(alpha = 0.6f),
    unfocusedContainerColor = KhnColors.Surface.copy(alpha = 0.6f),
)

/** Feuille « Ajouter à une playlist » : favoris, playlists existantes, ou nouvelle playlist. */
@Composable
fun AddToPlaylistSheet(
    playlists: List<Playlist>,
    onPick: (String) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = KhnColors.Surface,
    ) {
        Text(
            "Ajouter à une playlist",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.navigationBarsPadding()) {
            item {
                SheetRow("Nouvelle playlist", Icons.Rounded.Add, highlight = true) { creating = true }
                SheetRow("Favoris", Icons.Rounded.Favorite) { onPick(FAVORITES_PLAYLIST_ID) }
                HorizontalDivider(color = KhnColors.Outline, modifier = Modifier.padding(vertical = 4.dp))
            }
            items(playlists, key = { it.id }) { p ->
                SheetRow("${p.name}  ·  ${p.trackIds.size}", Icons.AutoMirrored.Rounded.QueueMusic) { onPick(p.id) }
            }
        }
    }
    if (creating) {
        TextInputDialog(
            title = "Nouvelle playlist",
            initial = "",
            confirmLabel = "Créer",
            onConfirm = { creating = false; onCreate(it) },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun SheetRow(label: String, icon: ImageVector, highlight: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(Khn.shapes.artwork)
                .background(if (highlight) KhnColors.Neon else androidx.compose.ui.graphics.SolidColor(KhnColors.SurfaceHigh)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Color.White) }
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
