package com.khn.mp3.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import android.app.Activity
import androidx.activity.result.IntentSenderRequest
import com.khn.mp3.ui.components.AddToPlaylistSheet
import com.khn.mp3.ui.components.DeleteConfirmDialog
import com.khn.mp3.ui.screens.BoostSheet
import com.khn.mp3.ui.screens.DownloadScreen
import com.khn.mp3.ui.screens.LibraryScreen
import com.khn.mp3.ui.screens.MiniPlayer
import com.khn.mp3.ui.screens.NowPlayingScreen
import com.khn.mp3.ui.screens.PlaylistDetailScreen
import com.khn.mp3.ui.screens.PlaylistsScreen
import com.khn.mp3.ui.screens.SearchScreen
import com.khn.mp3.ui.screens.SettingsScreen
import com.khn.mp3.ui.screens.SoundScreen
import com.khn.mp3.ui.theme.KhnColors
import kotlinx.coroutines.flow.Flow
import java.io.File

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    LIBRARY("library", "Musique", Icons.Rounded.LibraryMusic),
    SEARCH("search", "Recherche", Icons.Rounded.Search),
    PLAYLISTS("playlists", "Playlists", Icons.AutoMirrored.Rounded.QueueMusic),
    DOWNLOADS("downloads", "Télécharger", Icons.Rounded.Download),
    SETTINGS("settings", "Réglages", Icons.Rounded.Settings),
}

@Composable
fun KhnAppRoot(openPlayerEvents: Flow<Unit>, vm: MainViewModel = viewModel()) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val player by vm.playerState.collectAsStateWithLifecycle()
    val byId by vm.tracksById.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()

    var nowPlayingOpen by rememberSaveable { mutableStateOf(false) }
    var addToPlaylistIds by remember { mutableStateOf<List<String>?>(null) }
    var showBoost by remember { mutableStateOf(false) }

    // ---- Permissions & import de fichiers ----
    val audioPermissions = remember {
        when {
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
            Build.VERSION.SDK_INT <= 28 -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.refreshLibrary()
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.importFiles(uris)
    }
    val requestPermission = { permissionLauncher.launch(audioPermissions) }
    val importFiles = { importLauncher.launch(arrayOf("audio/*")) }

    LaunchedEffect(Unit) {
        // L'analyse est lancée par MainActivity.onStart ; ici on demande seulement la permission.
        if (!vm.library.hasAudioPermission()) requestPermission()
    }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    // Suppression définitive : fenêtre d'autorisation d'Android (10+).
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        vm.onDeleteApproval(it.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(Unit) {
        vm.deleteApprovals.collect { sender ->
            deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }
    val deleteConfirm by vm.deleteConfirm.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        vm.navigation.collect { route ->
            nowPlayingOpen = false
            nav.navigate(route) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
            }
        }
    }
    LaunchedEffect(Unit) { openPlayerEvents.collect { if (vm.playerState.value.hasMedia) nowPlayingOpen = true } }

    val currentTrack = player.currentId?.let { byId[it] }
    val onAddToPlaylist: (List<String>) -> Unit = { addToPlaylistIds = it }

    Box(Modifier.fillMaxSize().background(KhnColors.Background)) {
        AppBackground(settings.backgroundPath, settings.backgroundDim, settings.backgroundBlur)

        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                Column {
                    AnimatedVisibility(
                        visible = player.hasMedia,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                    ) {
                        MiniPlayer(
                            state = player,
                            track = currentTrack,
                            position = vm.position,
                            onOpen = { nowPlayingOpen = true },
                            onTogglePlay = vm.player::togglePlay,
                            onNext = vm.player::next,
                        )
                    }
                    BottomBar(nav)
                }
            },
        ) { padding ->
            AppNavHost(
                nav = nav,
                vm = vm,
                padding = padding,
                onAddToPlaylist = onAddToPlaylist,
                onRequestPermission = requestPermission,
                onImportFiles = importFiles,
            )
        }

        AnimatedVisibility(
            visible = nowPlayingOpen && player.hasMedia,
            enter = slideInVertically(tween(320)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200)),
        ) {
            BackHandler { nowPlayingOpen = false }
            NowPlayingScreen(
                state = player,
                track = currentTrack,
                tracksById = byId,
                position = vm.position,
                isFavorite = player.currentId in favorites,
                onClose = { nowPlayingOpen = false },
                onTogglePlay = vm.player::togglePlay,
                onNext = vm.player::next,
                onPrevious = vm.player::previous,
                onSeek = vm.player::seekTo,
                onToggleShuffle = vm.player::toggleShuffle,
                onCycleRepeat = vm.player::cycleRepeat,
                onToggleFavorite = { player.currentId?.let(vm::toggleFavorite) },
                onAddToPlaylist = { currentTrack?.let { addToPlaylistIds = listOf(it.id) } },
                onOpenSound = {
                    nowPlayingOpen = false
                    nav.navigate("sound") { launchSingleTop = true }
                },
                onSkipTo = vm.player::skipTo,
                onRemoveFromQueue = vm.player::removeFromQueue,
                boostMb = settings.sound.loudnessGain,
                onOpenBoost = { showBoost = true },
            )
        }
    }

    if (showBoost) {
        BoostSheet(
            vm = vm,
            onMoreSettings = {
                showBoost = false
                nowPlayingOpen = false
                nav.navigate("sound") { launchSingleTop = true }
            },
            onDismiss = { showBoost = false },
        )
    }

    deleteConfirm?.let { tracks ->
        DeleteConfirmDialog(tracks, onConfirm = vm::confirmDelete, onDismiss = vm::dismissDelete)
    }

    addToPlaylistIds?.let { ids ->
        AddToPlaylistSheet(
            playlists = playlists,
            onPick = { id -> vm.addToPlaylist(id, ids); addToPlaylistIds = null },
            onCreate = { name -> vm.createPlaylist(name, ids); addToPlaylistIds = null },
            onDismiss = { addToPlaylistIds = null },
        )
    }
}

@Composable
private fun AppNavHost(
    nav: NavHostController,
    vm: MainViewModel,
    padding: PaddingValues,
    onAddToPlaylist: (List<String>) -> Unit,
    onRequestPermission: () -> Unit,
    onImportFiles: () -> Unit,
) {
    NavHost(
        navController = nav,
        startDestination = Tab.LIBRARY.route,
        enterTransition = { fadeIn(tween(160)) },
        exitTransition = { fadeOut(tween(110)) },
        popEnterTransition = { fadeIn(tween(160)) },
        popExitTransition = { fadeOut(tween(110)) },
    ) {
        composable(Tab.LIBRARY.route) {
            LibraryScreen(vm, padding, onAddToPlaylist, onRequestPermission, onImportFiles)
        }
        composable(Tab.SEARCH.route) {
            SearchScreen(vm, padding, onAddToPlaylist, onOpenPlaylist = { nav.navigate("playlist/$it") })
        }
        composable(Tab.PLAYLISTS.route) {
            PlaylistsScreen(vm, padding, onOpen = { nav.navigate("playlist/$it") })
        }
        composable("playlist/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            PlaylistDetailScreen(
                vm = vm,
                playlistId = entry.arguments?.getString("id").orEmpty(),
                contentPadding = padding,
                onBack = { nav.popBackStack() },
                onAddToPlaylist = onAddToPlaylist,
            )
        }
        composable(Tab.DOWNLOADS.route) { DownloadScreen(vm, padding) }
        composable(Tab.SETTINGS.route) {
            SettingsScreen(vm, padding, onOpenSound = { nav.navigate("sound") }, onImportFiles = onImportFiles)
        }
        composable("sound") { SoundScreen(vm, padding, onBack = { nav.popBackStack() }) }
    }
}

@Composable
private fun BottomBar(nav: NavHostController) {
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val selected = when {
        route == null -> Tab.LIBRARY
        route.startsWith("playlist") -> Tab.PLAYLISTS
        route == "sound" -> Tab.SETTINGS
        else -> Tab.entries.firstOrNull { it.route == route } ?: Tab.LIBRARY
    }
    NavigationBar(containerColor = KhnColors.Background.copy(alpha = 0.94f), tonalElevation = 0.dp) {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = {
                    nav.navigate(tab.route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label, maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = KhnColors.Violet,
                    indicatorColor = KhnColors.Violet.copy(alpha = 0.85f),
                    unselectedIconColor = KhnColors.TextMuted,
                    unselectedTextColor = KhnColors.TextMuted,
                ),
            )
        }
    }
}

/**
 * Arrière-plan de l'app : photo choisie (assombrie + floutée sur Android 12+) ou halo néon par défaut.
 * Couche statique : elle n'est redessinée que si les réglages changent.
 */
@Composable
private fun AppBackground(path: String?, dim: Float, blur: Int) {
    if (path != null && File(path).exists()) {
        AsyncImage(
            model = File(path),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .then(if (blur > 0) Modifier.blur(blur.dp) else Modifier),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to KhnColors.Background.copy(alpha = dim * 0.75f),
                        0.6f to KhnColors.Background.copy(alpha = dim),
                        1f to KhnColors.Background.copy(alpha = (dim + 0.2f).coerceAtMost(1f)),
                    ),
                ),
        )
    } else {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(KhnColors.Background)
                    drawCircle(
                        Brush.radialGradient(
                            listOf(KhnColors.Violet.copy(alpha = 0.22f), Color.Transparent),
                            center = Offset(size.width * 0.1f, size.height * 0.05f),
                            radius = size.width * 0.9f,
                        ),
                        radius = size.width * 0.9f,
                        center = Offset(size.width * 0.1f, size.height * 0.05f),
                    )
                    drawCircle(
                        Brush.radialGradient(
                            listOf(KhnColors.Magenta.copy(alpha = 0.14f), Color.Transparent),
                            center = Offset(size.width * 0.95f, size.height * 0.75f),
                            radius = size.width * 0.8f,
                        ),
                        radius = size.width * 0.8f,
                        center = Offset(size.width * 0.95f, size.height * 0.75f),
                    )
                },
        )
    }
}
