package com.khn.mp3.ui.screens

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.khn.mp3.BuildConfig
import com.khn.mp3.data.SoundSettings
import com.khn.mp3.playback.EqPresets
import com.khn.mp3.ui.MainViewModel
import com.khn.mp3.ui.components.EqBandSlider
import com.khn.mp3.ui.components.NeonSlider
import com.khn.mp3.ui.components.OutlineButton
import com.khn.mp3.ui.components.SectionTitle
import com.khn.mp3.ui.theme.Khn
import com.khn.mp3.ui.theme.KhnColors
import java.io.File
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    vm: MainViewModel,
    contentPadding: PaddingValues,
    onOpenSound: () -> Unit,
    onImportFiles: () -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setBackgroundImage(uri)
    }

    var dim by remember(settings.backgroundDim) { mutableFloatStateOf(settings.backgroundDim) }
    var blur by remember(settings.backgroundBlur) { mutableFloatStateOf(settings.backgroundBlur.toFloat()) }
    var minDur by remember(settings.minDurationSec) { mutableFloatStateOf(settings.minDurationSec.toFloat()) }

    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        item {
            Text("Réglages", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp))
        }

        item { SectionTitle("Son") }
        item {
            SettingsCard {
                NavRow(
                    Icons.Rounded.Equalizer,
                    "Paramètres du son",
                    "Égaliseur ${if (settings.sound.eqEnabled) "activé · ${settings.sound.preset}" else "désactivé"} · vitesse ×${"%.2f".format(settings.sound.speed)}",
                    onOpenSound,
                )
            }
        }

        item { SectionTitle("Apparence") }
        item {
            SettingsCard {
                Text("Photo d'arrière-plan", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(width = 72.dp, height = 128.dp)
                            .clip(Khn.shapes.artwork)
                            .background(KhnColors.SurfaceHigh)
                            .clickable { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        val path = settings.backgroundPath
                        if (path != null) {
                            AsyncImage(model = File(path), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Icon(Icons.Rounded.Image, null, tint = KhnColors.TextMuted)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlineButton("Choisir une photo", Icons.Rounded.Image, {
                            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        })
                        if (settings.backgroundPath != null) {
                            OutlineButton("Retirer", Icons.Rounded.HideImage, vm::clearBackground)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                LabeledSlider("Assombrissement", "${(dim * 100).roundToInt()} %") {
                    NeonSlider(dim, { dim = it }, valueRange = 0f..0.9f, onValueChangeFinished = { vm.setBackgroundDim(dim) })
                }
                LabeledSlider(
                    "Flou",
                    if (Build.VERSION.SDK_INT >= 31) "${blur.roundToInt()}" else "Android 12+",
                ) {
                    NeonSlider(blur, { blur = it }, valueRange = 0f..25f, steps = 24, onValueChangeFinished = { vm.setBackgroundBlur(blur.roundToInt()) })
                }
            }
        }

        item { SectionTitle("Bibliothèque") }
        item {
            SettingsCard {
                NavRow(Icons.Rounded.FolderOpen, "Ajouter des fichiers audio", "Choisir des musiques dans n'importe quel dossier", onImportFiles)
                NavRow(Icons.Rounded.Refresh, "Analyser à nouveau", "${tracks.size} titres trouvés", vm::refreshLibrary)
                Spacer(Modifier.height(8.dp))
                LabeledSlider("Ignorer les sons de moins de", "${minDur.roundToInt()} s") {
                    NeonSlider(minDur, { minDur = it }, valueRange = 0f..120f, steps = 23, onValueChangeFinished = { vm.setMinDuration(minDur.roundToInt()) })
                }
                Text(
                    "Évite que les notes vocales et sons de notification apparaissent dans ta bibliothèque.",
                    style = MaterialTheme.typography.bodySmall,
                    color = KhnColors.TextMuted,
                )
            }
        }

        item { SectionTitle("À propos") }
        item {
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Info, null, tint = KhnColors.Violet)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("KHN MP3 · version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleSmall)
                        Text("Musique libre fournie par Internet Archive (archive.org).", style = MaterialTheme.typography.bodySmall, color = KhnColors.TextMuted)
                    }
                }
            }
        }
    }
}

@Composable
fun SoundScreen(vm: MainViewModel, contentPadding: PaddingValues, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val spec by vm.audioEffects.spec.collectAsStateWithLifecycle()
    val sound = settings.sound

    // État local pendant le glissement ; persistance au relâchement.
    var bands by remember { mutableStateOf(emptyList<Int>()) }
    LaunchedEffect(sound.bandLevels, sound.preset, spec) {
        bands = if (sound.bandLevels.size == spec.centerHz.size) sound.bandLevels else EqPresets.levelsFor(sound.preset, spec)
    }
    var bass by remember(sound.bassBoost) { mutableFloatStateOf(sound.bassBoost.toFloat()) }
    var virt by remember(sound.virtualizer) { mutableFloatStateOf(sound.virtualizer.toFloat()) }
    var loud by remember(sound.loudnessGain) { mutableFloatStateOf(sound.loudnessGain.toFloat()) }
    var speed by remember(sound.speed) { mutableFloatStateOf(sound.speed) }
    var pitch by remember(sound.pitchSemitones) { mutableFloatStateOf(sound.pitchSemitones.toFloat()) }

    fun live(): SoundSettings = sound.copy(
        bandLevels = bands, bassBoost = bass.roundToInt(), virtualizer = virt.roundToInt(), loudnessGain = loud.roundToInt(),
    )

    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
                Text("Paramètres du son", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = vm::resetSound) { Icon(Icons.Rounded.RestartAlt, "Réinitialiser") }
            }
        }

        item {
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Égaliseur", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (spec.available) "${spec.centerHz.size} bandes · ${sound.preset}" else "Lance une musique pour activer l'égaliseur",
                            style = MaterialTheme.typography.bodySmall,
                            color = KhnColors.TextMuted,
                        )
                    }
                    Switch(
                        checked = sound.eqEnabled,
                        onCheckedChange = vm::setEqEnabled,
                        colors = SwitchDefaults.colors(checkedTrackColor = KhnColors.Violet, checkedThumbColor = Color.White),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EqPresets.all.forEach { (name, _) ->
                        FilterChip(
                            selected = sound.preset == name,
                            onClick = {
                                val levels = EqPresets.levelsFor(name, spec)
                                bands = levels
                                vm.setBands(levels, name)
                                if (!sound.eqEnabled) vm.setEqEnabled(true)
                            },
                            label = { Text(name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = KhnColors.Violet,
                                selectedLabelColor = Color.White,
                                containerColor = KhnColors.SurfaceHigh,
                            ),
                            border = null,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                val range = (spec.maxLevel - spec.minLevel).coerceAtLeast(1) / 2f
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    spec.centerHz.forEachIndexed { i, hz ->
                        val level = bands.getOrElse(i) { 0 }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "%+d".format((level / 100f).roundToInt()),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (sound.eqEnabled) KhnColors.Text else KhnColors.TextMuted,
                            )
                            Spacer(Modifier.height(6.dp))
                            EqBandSlider(
                                value = level / range,
                                onChange = { v ->
                                    val newLevel = (v * range).roundToInt().coerceIn(spec.minLevel, spec.maxLevel)
                                    bands = bands.toMutableList().also { list ->
                                        while (list.size < spec.centerHz.size) list.add(0)
                                        list[i] = newLevel
                                    }
                                    vm.previewSound(live().copy(eqEnabled = true))
                                },
                                onChangeFinished = {
                                    vm.setBands(bands, "Personnalisé")
                                    if (!sound.eqEnabled) vm.setEqEnabled(true)
                                },
                                modifier = Modifier.height(190.dp),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (hz >= 1000) "${"%.1f".format(hz / 1000f).removeSuffix(",0").removeSuffix(".0")}k" else "$hz",
                                style = MaterialTheme.typography.labelSmall,
                                color = KhnColors.TextMuted,
                            )
                        }
                    }
                }
                Text("Hz", style = MaterialTheme.typography.labelSmall, color = KhnColors.TextMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }

        item {
            SettingsCard {
                Text("Effets", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                LabeledSlider("Renfort des basses", "${(bass / 10).roundToInt()} %") {
                    NeonSlider(bass, { bass = it; vm.previewSound(live()) }, valueRange = 0f..1000f, onValueChangeFinished = { vm.setBassBoost(bass.roundToInt()) })
                }
                LabeledSlider("Spatialisation 3D", "${(virt / 10).roundToInt()} %") {
                    NeonSlider(virt, { virt = it; vm.previewSound(live()) }, valueRange = 0f..1000f, onValueChangeFinished = { vm.setVirtualizer(virt.roundToInt()) })
                }
                LabeledSlider("Amplification du volume", "+${"%.1f".format(loud / 100f)} dB") {
                    NeonSlider(loud, { loud = it; vm.previewSound(live()) }, valueRange = 0f..1000f, onValueChangeFinished = { vm.setLoudness(loud.roundToInt()) })
                }
                Text(
                    "L'effet des basses et de la spatialisation dépend du téléphone et s'entend surtout au casque.",
                    style = MaterialTheme.typography.bodySmall,
                    color = KhnColors.TextMuted,
                )
            }
        }

        item {
            SettingsCard {
                Text("Lecture", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                LabeledSlider("Vitesse", "×${"%.2f".format(speed)}") {
                    NeonSlider(speed, { speed = (it * 20).roundToInt() / 20f }, valueRange = 0.5f..2f, onValueChangeFinished = { vm.setSpeed(speed) })
                }
                LabeledSlider("Tonalité", "${if (pitch > 0) "+" else ""}${pitch.roundToInt()} demi-ton${if (kotlin.math.abs(pitch.roundToInt()) > 1) "s" else ""}") {
                    NeonSlider(pitch, { pitch = it }, valueRange = -6f..6f, steps = 11, onValueChangeFinished = { vm.setPitch(pitch.roundToInt()) })
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(Khn.shapes.card)
            .background(KhnColors.Surface.copy(alpha = 0.82f))
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Khn.shapes.artwork)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(Khn.shapes.artwork).background(KhnColors.Neon), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = KhnColors.TextMuted)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = KhnColors.TextMuted)
    }
}

@Composable
private fun LabeledSlider(label: String, value: String, slider: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.labelLarge, color = KhnColors.Violet)
        }
        slider()
    }
}
