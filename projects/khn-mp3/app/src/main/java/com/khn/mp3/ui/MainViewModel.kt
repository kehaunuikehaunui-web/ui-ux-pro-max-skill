package com.khn.mp3.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.khn.mp3.container
import com.khn.mp3.data.ArchiveRepository
import com.khn.mp3.data.DownloadRepository
import com.khn.mp3.data.FAVORITES_PLAYLIST_ID
import com.khn.mp3.data.Playlist
import com.khn.mp3.data.SortOrder
import com.khn.mp3.data.Track
import com.khn.mp3.data.normalize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class LibraryFilter(val label: String) {
    ALL("Tous"), FAVORITES("Favoris"), DOWNLOADED("Téléchargés"), IMPORTED("Importés")
}

@Immutable
data class SearchResults(
    val query: String = "",
    val tracks: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
)

@Immutable
data class ArchiveUi(
    val query: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val items: List<ArchiveRepository.Item> = emptyList(),
    val searched: Boolean = false,
    val expanded: String? = null,
    val files: Map<String, FilesState> = emptyMap(),
)

@Immutable
data class FilesState(
    val loading: Boolean = true,
    val error: String? = null,
    val files: List<ArchiveRepository.AudioFile> = emptyList(),
)

@OptIn(FlowPreview::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val c = app.container
    val player = c.player
    val library = c.library
    val audioEffects = c.audioEffects

    val tracks = library.tracks
    val tracksById = library.byId
    val favorites = c.userData.favorites
    val settings = c.settings.settings
    val scanning = library.scanning
    val hasPermission = library.hasPermission
    val playerState = player.state
    val position = player.position

    /** Favoris dans l'ordre d'ajout (le plus récent en premier). */
    val favoritesOrdered: StateFlow<List<String>> = c.userData.data
        .map { it.favorites }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val playlists: StateFlow<List<Playlist>> = c.userData.data
        .map { it.playlists }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    private fun toast(msg: String) { _messages.tryEmit(msg) }

    // ---------------- Bibliothèque ----------------

    private val _filter = MutableStateFlow(LibraryFilter.ALL)
    val filter: StateFlow<LibraryFilter> = _filter.asStateFlow()
    fun setFilter(f: LibraryFilter) { _filter.value = f }

    val libraryTracks: StateFlow<List<Track>> = combine(
        tracks, favorites, _filter, settings.map { it.sort },
    ) { all, favs, filter, sort ->
        val filtered = when (filter) {
            LibraryFilter.ALL -> all
            LibraryFilter.FAVORITES -> all.filter { it.id in favs }
            LibraryFilter.DOWNLOADED -> all.filter { it.isDownloaded }
            LibraryFilter.IMPORTED -> all.filter { it.source == Track.Source.IMPORTED }
        }
        when (sort) {
            SortOrder.TITLE -> filtered // déjà trié par titre
            SortOrder.ARTIST -> filtered.sortedBy { normalize(it.artist) }
            SortOrder.RECENT -> filtered.sortedByDescending { it.dateAdded }
            SortOrder.DURATION -> filtered.sortedByDescending { it.durationMs }
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setSort(order: SortOrder) = c.settings.setSort(order)
    fun refreshLibrary() = library.refresh()

    fun importFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val n = library.importDocuments(uris)
            toast(if (n > 0) "$n fichier(s) ajouté(s) à ta bibliothèque" else "Aucun fichier audio lisible")
        }
    }

    fun removeImported(track: Track) {
        if (track.source != Track.Source.IMPORTED) return
        c.userData.removeImported(track.uri.toString())
        toast("Retiré de la bibliothèque")
    }

    // ---------------- Lecture ----------------

    fun play(list: List<Track>, index: Int) = player.play(list, index)
    fun shuffle(list: List<Track>) = player.play(list, 0, shuffle = true)
    fun playNext(t: Track) { player.playNext(t); toast("Lecture après le titre en cours") }
    fun addToQueue(t: Track) { player.addToQueue(t); toast("Ajouté à la file d'attente") }

    fun playExternal(uri: Uri) {
        viewModelScope.launch {
            val known = tracks.value.firstOrNull { it.uri == uri }
            player.play(listOf(known ?: library.externalTrack(uri)), 0)
        }
    }

    // ---------------- Favoris & playlists ----------------

    fun toggleFavorite(id: String) = c.userData.toggleFavorite(id)

    fun createPlaylist(name: String, initial: List<String> = emptyList()): String {
        val id = c.userData.createPlaylist(name, initial)
        toast("Playlist « ${name.trim().ifEmpty { "Ma playlist" }} » créée")
        return id
    }

    fun renamePlaylist(id: String, name: String) = c.userData.renamePlaylist(id, name)
    fun deletePlaylist(id: String) { c.userData.deletePlaylist(id); toast("Playlist supprimée") }

    fun addToPlaylist(playlistId: String, trackIds: List<String>) {
        if (playlistId == FAVORITES_PLAYLIST_ID) {
            val favs = favorites.value
            trackIds.filterNot { it in favs }.forEach { c.userData.toggleFavorite(it) }
            toast("Ajouté aux favoris")
            return
        }
        c.userData.addToPlaylist(playlistId, trackIds)
        val name = playlists.value.firstOrNull { it.id == playlistId }?.name ?: "la playlist"
        toast(if (trackIds.size == 1) "Ajouté à $name" else "${trackIds.size} titres ajoutés à $name")
    }

    fun removeFromPlaylist(playlistId: String, trackId: String) {
        if (playlistId == FAVORITES_PLAYLIST_ID) c.userData.toggleFavorite(trackId)
        else c.userData.removeFromPlaylist(playlistId, trackId)
    }

    fun movePlaylistItem(playlistId: String, from: Int, to: Int) = c.userData.movePlaylistItem(playlistId, from, to)

    // ---------------- Recherche ----------------

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    fun setQuery(q: String) { _query.value = q }

    val searchResults: StateFlow<SearchResults> = combine(
        _query.debounce(120), tracks, playlists,
    ) { q, all, pls ->
        val tokens = normalize(q).split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) SearchResults(q)
        else SearchResults(
            query = q,
            tracks = all.filter { t -> tokens.all { it in t.searchKey } }.take(300),
            playlists = pls.filter { p -> tokens.all { it in normalize(p.name) } },
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    // ---------------- Téléchargements ----------------

    private val _archive = MutableStateFlow(ArchiveUi())
    val archive: StateFlow<ArchiveUi> = _archive.asStateFlow()
    private var archiveJob: Job? = null

    fun setArchiveQuery(q: String) = _archive.update { it.copy(query = q) }

    fun searchArchive() {
        val q = _archive.value.query.trim()
        if (q.isEmpty()) return
        archiveJob?.cancel()
        archiveJob = viewModelScope.launch {
            _archive.update { it.copy(loading = true, error = null, expanded = null) }
            runCatching { c.archive.search(q) }
                .onSuccess { items -> _archive.update { it.copy(loading = false, items = items, searched = true) } }
                .onFailure { _archive.update { it.copy(loading = false, error = "Recherche impossible. Vérifie ta connexion Internet.", searched = true) } }
        }
    }

    fun toggleArchiveItem(item: ArchiveRepository.Item) {
        val current = _archive.value
        if (current.expanded == item.identifier) {
            _archive.update { it.copy(expanded = null) }
            return
        }
        _archive.update { it.copy(expanded = item.identifier) }
        if (current.files[item.identifier]?.files?.isNotEmpty() == true) return
        viewModelScope.launch {
            _archive.update { it.copy(files = it.files + (item.identifier to FilesState())) }
            val result = runCatching { c.archive.files(item) }
            _archive.update { s ->
                s.copy(files = s.files + (item.identifier to result.fold(
                    onSuccess = { FilesState(loading = false, files = it, error = if (it.isEmpty()) "Aucun MP3 dans cet album" else null) },
                    onFailure = { FilesState(loading = false, error = "Chargement impossible") },
                )))
            }
        }
    }

    fun previewStream(file: ArchiveRepository.AudioFile) = player.playStream(file.url, file.title, file.creator)

    fun download(url: String, title: String? = null, fileName: String? = null): Boolean {
        val id = c.downloads.enqueue(url, title, fileName)
        toast(if (id != null) "Téléchargement lancé : ${title ?: "fichier"}" else "Lien invalide : il doit commencer par http:// ou https://")
        return id != null
    }

    fun cancelDownload(id: Long) = c.downloads.cancel(id)
    fun clearDownloads() = c.userData.clearDownloads()

    /** Statuts rafraîchis chaque seconde, uniquement quand l'écran Téléchargements est visible. */
    val downloadStatuses: StateFlow<List<DownloadRepository.Status>> = flow {
        while (true) {
            emit(c.downloads.statuses(c.userData.data.value.downloads))
            delay(1000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(1_000), emptyList())

    // ---------------- Son ----------------

    fun previewSound(s: com.khn.mp3.data.SoundSettings) = audioEffects.preview(s)
    fun setEqEnabled(v: Boolean) = c.settings.setEqEnabled(v)
    fun setBands(levels: List<Int>, preset: String) = c.settings.setBands(levels, preset)
    fun setBassBoost(v: Int) = c.settings.setBassBoost(v)
    fun setVirtualizer(v: Int) = c.settings.setVirtualizer(v)
    fun setLoudness(v: Int) = c.settings.setLoudness(v)
    fun setSpeed(v: Float) = c.settings.setSpeed(v)
    fun setPitch(v: Int) = c.settings.setPitch(v)
    fun resetSound() { c.settings.resetSound(); toast("Paramètres du son réinitialisés") }

    // ---------------- Apparence ----------------

    fun setBackgroundImage(uri: Uri) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) { copyBackground(uri) }
            if (path == null) toast("Impossible de lire cette image")
            else {
                settings.value.backgroundPath?.let { old -> runCatching { File(old).delete() } }
                c.settings.setBackground(path)
                toast("Photo d'arrière-plan appliquée")
            }
        }
    }

    fun clearBackground() {
        settings.value.backgroundPath?.let { runCatching { File(it).delete() } }
        c.settings.setBackground(null)
    }

    fun setBackgroundDim(v: Float) = c.settings.setBackgroundDim(v)
    fun setBackgroundBlur(v: Int) = c.settings.setBackgroundBlur(v)
    fun setMinDuration(sec: Int) = c.settings.setMinDuration(sec)

    /** Copie redimensionnée (max 1440 px) dans le stockage privé : l'image reste disponible même si l'originale est supprimée. */
    private fun copyBackground(uri: Uri): String? = runCatching {
        val resolver = getApplication<Application>().contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1440) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val rotation = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val upright = if (rotation == 0f) bitmap else
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation) }, true)
                .also { if (it !== bitmap) bitmap.recycle() }
        val out = File(getApplication<Application>().filesDir, "background_${System.currentTimeMillis()}.jpg")
        out.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        upright.recycle()
        out.absolutePath
    }.getOrNull()
}
