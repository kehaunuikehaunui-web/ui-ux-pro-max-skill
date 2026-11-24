package com.khn.mp3.ui.screens

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.khn.mp3.data.CoverStyle
import com.khn.mp3.data.Track
import com.khn.mp3.data.coverFor
import com.khn.mp3.data.formatDuration
import com.khn.mp3.playback.PlayerState
import com.khn.mp3.playback.SleepTimer
import com.khn.mp3.ui.MainViewModel
import com.khn.mp3.ui.components.LocalCovers
import com.khn.mp3.ui.components.NeonSlider
import com.khn.mp3.ui.components.PlayerCover
import com.khn.mp3.ui.components.TrackArtwork
import com.khn.mp3.ui.components.artworkModel
import com.khn.mp3.ui.components.rememberArtworkAccent
import com.khn.mp3.ui.theme.Khn
import com.khn.mp3.ui.theme.KhnColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun externalUriFor(state: PlayerState): Uri? =
    state.currentId?.takeIf { it.startsWith("ext:") }?.let { Uri.parse(it.removePrefix("ext:")) }

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
            TrackArtwork(track, Modifier.size(46.dp), fallbackUri = externalUriFor(state), seed = state.currentId.orEmpty())
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
    vm: MainViewModel,
    state: PlayerState,
    track: Track?,
    onClose: () -> Unit,
    onOpenSound: () -> Unit,
    onOpenBoost: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val byId by vm.tracksById.collectAsStateWithLifecycle()
    val sleep by vm.sleepTimer.collectAsStateWithLifecycle()
    val covers = LocalCovers.current

    val scope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    val fallback = externalUriFor(state)
    val seed = state.currentId.orEmpty()
    val isFavorite = state.currentId in favorites

    // Couleur d'accent tirée de la pochette (animée entre deux morceaux).
    val accentRaw = rememberArtworkAccent(
        artworkModel(track?.uri ?: fallback, covers.coverFor(track)),
        enabled = settings.dynamicColors,
    )
    val accent by animateColorAsState(accentRaw ?: KhnColors.Violet, tween(600), label = "accent")
    val accent2 by animateColorAsState(
        accentRaw?.let { lerpTo(it, KhnColors.Magenta, 0.35f) } ?: KhnColors.Magenta, tween(600), label = "accent2",
    )

    val lyricsPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && track != null) vm.importLyrics(track, uri)
    }

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
        // Fond : pochette floutée (Android 12+) + dégradé aux couleurs de la pochette.
        if (Build.VERSION.SDK_INT >= 31) {
            TrackArtwork(
                track, fallbackUri = fallback, seed = seed,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.45f }.blur(80.dp),
                shape = RoundedCornerShape(0.dp),
                iconSize = 0.dp,
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to accent.copy(alpha = 0.42f),
                        0.45f to KhnColors.Background.copy(alpha = 0.72f),
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
            // ---- Barre du haut ----
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Fermer", modifier = Modifier.size(32.dp))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("LECTURE EN COURS", style = MaterialTheme.typography.labelSmall, color = KhnColors.TextMuted)
                    Text(
                        if (state.queue.isNotEmpty()) "${state.index + 1} / ${state.queue.size}" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (track != null) DropdownMenuItem(
                            text = { Text("Changer la pochette") },
                            leadingIcon = { Icon(Icons.Rounded.Image, null) },
                            onClick = { menu = false; vm.askCover(track) },
                        )
                        DropdownMenuItem(
                            text = { Text("Égaliseur et son") },
                            leadingIcon = { Icon(Icons.Rounded.Equalizer, null) },
                            onClick = { menu = false; onOpenSound() },
                        )
                        HorizontalDivider()
                        Text(
                            "Style de la pochette",
                            style = MaterialTheme.typography.labelMedium,
                            color = KhnColors.TextMuted,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                        CoverStyle.entries.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.label) },
                                trailingIcon = { if (settings.coverStyle == s) Icon(Icons.Rounded.Check, null, tint = accent) },
                                onClick = { menu = false; vm.setCoverStyle(s) },
                            )
                        }
                    }
                }
            }

            // ---- Pochette ou paroles ----
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = showLyrics,
                    transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) },
                    label = "coverOrLyrics",
                ) { lyricsMode ->
                    if (lyricsMode) {
                        LyricsPanel(
                            vm = vm,
                            track = track,
                            position = vm.position,
                            accent = accent,
                            onSeek = vm.player::seekTo,
                            onImport = { lyricsPicker.launch(arrayOf("*/*")) },
                        )
                    } else {
                        SwipeableCover(
                            onNext = vm.player::next,
                            onPrevious = vm.player::previous,
                        ) {
                            PlayerCover(
                                track = track,
                                fallbackUri = fallback,
                                seed = seed,
                                style = settings.coverStyle,
                                isPlaying = state.isPlaying,
                                glow = accent,
                                modifier = Modifier.fillMaxWidth(0.86f),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---- Titre ----
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
                IconButton(onClick = { state.currentId?.let(vm::toggleFavorite) }, enabled = track != null) {
                    Icon(
                        if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favori",
                        tint = if (isFavorite) KhnColors.Magenta else KhnColors.Text,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            SeekBar(vm.position, state.durationMs, accent, vm.player::seekTo)
            Spacer(Modifier.height(8.dp))

            // ---- Commandes ----
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = vm.player::toggleShuffle) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = "Aléatoire", tint = if (state.shuffle) accent else KhnColors.TextMuted)
                }
                IconButton(onClick = vm.player::previous, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Précédent", modifier = Modifier.size(40.dp))
                }
                Box(
                    Modifier
                        .size(76.dp)
                        .shadow(24.dp, CircleShape, ambientColor = accent2, spotColor = accent)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(accent, accent2)))
                        .clickable(onClick = vm.player::togglePlay),
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
                IconButton(onClick = vm.player::next, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Suivant", modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = vm.player::cycleRepeat) {
                    Icon(
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = "Répéter",
                        tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) accent else KhnColors.TextMuted,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---- Barre d'outils ----
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onAddToPlaylist, enabled = track != null) {
                    Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = "Ajouter à une playlist")
                }
                IconButton(onClick = { showLyrics = !showLyrics }) {
                    Icon(Icons.Rounded.Subtitles, contentDescription = "Paroles", tint = if (showLyrics) accent else KhnColors.Text)
                }
                BoostPill(settings.sound.loudnessGain, accent, accent2, onOpenBoost)
                SleepButton(sleep, accent) { showSleep = true }
                IconButton(onClick = { showQueue = true }) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "File d'attente")
                }
            }
        }
    }

    if (showQueue) {
        QueueSheet(
            state = state,
            tracksById = byId,
            onSkipTo = vm.player::skipTo,
            onRemove = vm.player::removeFromQueue,
            onMove = vm::moveQueueItem,
            onDismiss = { showQueue = false },
        )
    }
    if (showSleep) {
        SleepTimerSheet(
            state = sleep,
            onStart = { vm.startSleepTimer(it); showSleep = false },
            onEndOfTrack = { vm.sleepAtEndOfTrack(); showSleep = false },
            onCancel = { vm.cancelSleepTimer(); showSleep = false },
            onDismiss = { showSleep = false },
        )
    }
}

/** Glisser la pochette à gauche / à droite pour changer de morceau. */
@Composable
private fun SwipeableCover(onNext: () -> Unit, onPrevious: () -> Unit, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(1) }
    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { width = it.width }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            val threshold = width * 0.25f
                            val value = offsetX.value
                            if (abs(value) < threshold) {
                                offsetX.animateTo(0f, spring())
                                return@launch
                            }
                            val toLeft = value < 0
                            offsetX.animateTo(if (toLeft) -width.toFloat() else width.toFloat(), tween(160))
                            if (toLeft) onNext() else onPrevious()
                            offsetX.snapTo(if (toLeft) width.toFloat() else -width.toFloat())
                            offsetX.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 400f))
                        }
                    },
                    onDragCancel = { scope.launch { offsetX.animateTo(0f, spring()) } },
                ) { change, drag ->
                    change.consume()
                    scope.launch { offsetX.snapTo(offsetX.value + drag) }
                }
            }
            .graphicsLayer {
                translationX = offsetX.value
                alpha = 1f - (abs(offsetX.value) / width.coerceAtLeast(1)).coerceIn(0f, 0.6f)
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun BoostPill(boostMb: Int, accent: Color, accent2: Color, onClick: () -> Unit) {
    val boosted = boostMb > 0
    Row(
        Modifier
            .clip(Khn.shapes.pill)
            .background(if (boosted) Brush.horizontalGradient(listOf(accent, accent2)) else SolidColor(KhnColors.SurfaceHigh.copy(alpha = 0.8f)))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            if (boosted) "+${"%.0f".format(boostMb / 100f)} dB" else "Amplifier",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
        )
    }
}

@Composable
private fun SleepButton(state: SleepTimer.State, accent: Color, onClick: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state) {
        while (state is SleepTimer.State.At) {
            now = System.currentTimeMillis()
            delay(15_000)
        }
    }
    val active = state !is SleepTimer.State.Off
    Row(
        Modifier.clip(Khn.shapes.pill).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Bedtime, contentDescription = "Minuterie", tint = if (active) accent else KhnColors.Text)
        when (state) {
            is SleepTimer.State.At -> {
                val min = ((state.endAtMs - now + 59_999) / 60_000).coerceAtLeast(0)
                Spacer(Modifier.width(4.dp))
                Text("$min min", style = MaterialTheme.typography.labelMedium, color = accent)
            }
            SleepTimer.State.EndOfTrack -> {
                Spacer(Modifier.width(4.dp))
                Text("fin", style = MaterialTheme.typography.labelMedium, color = accent)
            }
            SleepTimer.State.Off -> Unit
        }
    }
}

@Composable
private fun SeekBar(position: StateFlow<Long>, durationMs: Long, accent: Color, onSeek: (Long) -> Unit) {
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
            activeColor = accent,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration((shown * duration).toLong()), style = MaterialTheme.typography.labelMedium, color = KhnColors.TextMuted)
            Text(formatDuration(durationMs), style = MaterialTheme.typography.labelMedium, color = KhnColors.TextMuted)
        }
    }
}

/** Mélange deux couleurs (t = 0 → a, t = 1 → b). */
private fun lerpTo(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)
