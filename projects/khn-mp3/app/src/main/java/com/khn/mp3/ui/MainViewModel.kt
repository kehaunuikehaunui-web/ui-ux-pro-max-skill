package com.khn.mp3.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.khn.mp3.container
import com.khn.mp3.data.ArchiveRepository
import com.khn.mp3.data.DownloadRepository
import com.khn.mp3.data.FAVORITES_PLAYLIST_ID
import com.khn.mp3.data.FreeTrack
import com.khn.mp3.data.TrashEntry
import com.khn.mp3.data.JamendoRepository
import com.khn.mp3.data.isYoutubeUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
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
import kotlinx.coroutines.flow.first
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

enum class CatalogSource(val label: String) {
    ARCHIVE("Internet Archive"), CCMIXTER("ccMixter"), JAMENDO("Jamendo")
}

@Immutable
data class ArchiveUi(
    val source: CatalogSource = CatalogSource.ARCHIVE,
    val tracks: List<FreeTrack> = emptyList(),
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

    init {
        viewModelScope.launch(Dispatchers.IO) {
            c.userData.loaded.first { it }
            c.trash.purgeExpired()
        }
        viewModelScope.launch {
            library.newTracks.collect { n ->
                toast(if (n == 1) "1 nouvelle musique dans ta bibliothèque" else "$n nouvelles musiques dans ta bibliothèque")
            }
        }
    }

    // ---------------- Navigation & partage entrant ----------------

    private val _navigation = Channel<String>(Channel.BUFFERED)
    /** Routes à ouvrir (ex. après un partage vers KHN MP3). */
    val navigation = _navigation.receiveAsFlow()

    private val _sharedUrl = MutableStateFlow<String?>(null)
    val sharedUrl: StateFlow<String?> = _sharedUrl.asStateFlow()
    fun consumeSharedUrl() { _sharedUrl.value = null }

    /** Fichiers audio partagés depuis une autre app : copiés dans Musique/KHN MP3. */
    fun importShared(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val n = c.sharedImporter.import(uris)
            toast(
                if (n > 0) "$n musique${if (n > 1) "s" else ""} ajoutée${if (n > 1) "s" else ""} dans Musique/KHN MP3"
                else "Impossible d'enregistrer ce fichier audio",
            )
            if (n > 0) {
                _filter.value = LibraryFilter.DOWNLOADED
                _navigation.trySend("library")
            }
        }
    }

    /** Texte partagé : on récupère le premier lien et on prépare son téléchargement. */
    fun handleSharedText(text: String) {
        val url = Regex("https?://\\S+").find(text)?.value?.trimEnd('.', ',', ')', '"', '\'')
        when {
            url == null -> toast("Aucun lien trouvé dans le texte partagé")
            isYoutubeUrl(url) -> toast(YOUTUBE_REFUSED)
            else -> _sharedUrl.value = url
        }
        _navigation.trySend("downloads")
    }

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

    // ---------------- Corbeille ----------------

    /** Contenu de la corbeille, le plus récent en premier. */
    val trash: StateFlow<List<TrashEntry>> = c.userData.data
        .map { d -> d.trash.sortedByDescending { it.deletedAt } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _systemRequests = Channel<IntentSender>(Channel.BUFFERED)
    /** Fenêtres d'autorisation d'Android à afficher (corbeille, restauration, suppression). */
    val systemRequests = _systemRequests.receiveAsFlow()

    private sealed interface Pending {
        class Trash(val entries: List<TrashEntry>) : Pending
        class Restore(val entries: List<TrashEntry>) : Pending
        class DeleteForever(val entries: List<TrashEntry>) : Pending
        /** Android 10 : une autorisation par fichier, la copie attend dans la corbeille interne. */
        class Legacy(val queue: ArrayDeque<Triple<Track, IntentSender, TrashEntry>>) : Pending
    }

    private var pending: Pending? = null
    private var movedCount = 0
    private var failedCount = 0

    /** « Supprimer » : met à la corbeille (récupérable 30 jours). */
    fun moveToTrash(tracks: List<Track>) {
        if (tracks.isEmpty() || pending != null) return
        movedCount = 0
        failedCount = 0
        val deleter = c.deleter
        viewModelScope.launch {
            val withUri = if (Build.VERSION.SDK_INT >= 30) {
                withContext(Dispatchers.IO) { tracks.map { it to deleter.mediaStoreUri(it) } }
            } else tracks.map { it to null }
            val viaSystem = withUri.filter { it.second != null }
            val viaCopy = withUri.filter { it.second == null }.map { it.first }

            // Corbeille interne : copie, puis suppression de l'original.
            val legacyQueue = ArrayDeque<Triple<Track, IntentSender, TrashEntry>>()
            for (t in viaCopy) {
                val staged = c.trash.stageCopy(t)
                if (staged == null) { failedCount++; continue }
                val r = withContext(Dispatchers.IO) { deleter.deleteNow(listOf(t)) }
                when {
                    r.deleted.isNotEmpty() -> trashedCopy(t, staged)
                    r.needsApproval.isNotEmpty() -> legacyQueue.addLast(Triple(t, r.needsApproval.first().second, staged))
                    else -> { c.trash.discard(staged); failedCount++ }
                }
            }

            if (viaSystem.isNotEmpty() && Build.VERSION.SDK_INT >= 30) {
                val entries = viaSystem.map { (t, uri) ->
                    c.trash.entryFor(t, system = true, mediaUri = uri, file = null, mime = "audio/*", fileName = t.title)
                }
                pending = Pending.Trash(entries)
                _systemRequests.send(deleter.trashRequest(viaSystem.mapNotNull { it.second }, trashed = true))
                return@launch
            }
            if (legacyQueue.isNotEmpty()) {
                pending = Pending.Legacy(legacyQueue)
                _systemRequests.send(legacyQueue.first().second)
                return@launch
            }
            finishTrash(cancelled = false)
        }
    }

    private fun trashedCopy(t: Track, staged: TrashEntry) {
        c.trash.commitCopies(listOf(staged))
        player.removeIds(setOf(t.id))
        movedCount++
    }

    private fun finishTrash(cancelled: Boolean) {
        library.refresh()
        toast(
            when {
                movedCount > 0 && failedCount > 0 -> "$movedCount dans la corbeille · $failedCount impossible(s) à supprimer"
                movedCount == 1 -> "1 musique mise à la corbeille"
                movedCount > 1 -> "$movedCount musiques mises à la corbeille"
                cancelled -> "Suppression annulée"
                else -> "Impossible de supprimer ce fichier"
            },
        )
    }

    /** Réponse aux fenêtres d'autorisation d'Android. */
    fun onSystemApproval(approved: Boolean) {
        val p = pending ?: return
        viewModelScope.launch {
            when (p) {
                is Pending.Trash -> {
                    pending = null
                    if (approved) {
                        c.trash.commitSystem(p.entries)
                        player.removeIds(p.entries.mapTo(HashSet()) { it.originalId })
                        movedCount += p.entries.size
                    }
                    finishTrash(cancelled = !approved)
                }
                is Pending.Legacy -> {
                    val (track, _, staged) = p.queue.removeFirst()
                    if (approved) {
                        val r = withContext(Dispatchers.IO) { c.deleter.deleteNow(listOf(track)) }
                        if (r.deleted.isNotEmpty()) trashedCopy(track, staged)
                        else { c.trash.discard(staged); failedCount++ }
                        p.queue.firstOrNull()?.let {
                            _systemRequests.send(it.second)
                            return@launch
                        }
                    } else {
                        c.trash.discard(staged)
                        p.queue.forEach { c.trash.discard(it.third) }
                        p.queue.clear()
                    }
                    pending = null
                    finishTrash(cancelled = !approved)
                }
                is Pending.Restore -> {
                    pending = null
                    if (approved) {
                        c.trash.onSystemRestored(p.entries)
                        library.refresh()
                        toast(restoredMessage(p.entries.size))
                    } else toast("Restauration annulée")
                }
                is Pending.DeleteForever -> {
                    pending = null
                    if (approved) {
                        c.trash.onDeletedForever(p.entries)
                        toast(deletedForeverMessage(p.entries.size))
                    } else toast("Suppression annulée")
                }
            }
        }
    }

    /** Restaure des musiques de la corbeille (favoris et playlists compris). */
    fun restoreFromTrash(entries: List<TrashEntry>) {
        if (entries.isEmpty() || pending != null) return
        viewModelScope.launch {
            val copies = entries.filterNot { it.system }
            val system = entries.filter { it.system && it.mediaUri != null }
            if (copies.isNotEmpty()) {
                val n = c.trash.restoreCopies(copies)
                library.refresh()
                toast(if (n == copies.size) restoredMessage(n) else "$n restaurée(s) · ${copies.size - n} impossible(s) à restaurer")
            }
            if (system.isNotEmpty() && Build.VERSION.SDK_INT >= 30) {
                val request = runCatching {
                    c.deleter.trashRequest(system.map { Uri.parse(it.mediaUri) }, trashed = false)
                }.getOrNull()
                if (request == null) {
                    // Fichiers déjà effacés par Android (délai dépassé) : on retire leurs fiches.
                    c.trash.onDeletedForever(system)
                    toast("Ces musiques ont déjà été effacées définitivement par Android")
                    return@launch
                }
                pending = Pending.Restore(system)
                _systemRequests.send(request)
            }
        }
    }

    private val _foreverConfirm = MutableStateFlow<List<TrashEntry>?>(null)
    /** Musiques en attente de confirmation de suppression définitive. */
    val foreverConfirm: StateFlow<List<TrashEntry>?> = _foreverConfirm.asStateFlow()
    fun askDeleteForever(entries: List<TrashEntry>) { if (entries.isNotEmpty()) _foreverConfirm.value = entries }
    fun dismissDeleteForever() { _foreverConfirm.value = null }

    fun confirmDeleteForever() {
        val entries = _foreverConfirm.value ?: return
        _foreverConfirm.value = null
        if (pending != null) return
        viewModelScope.launch {
            val copies = entries.filterNot { it.system }
            val system = entries.filter { it.system && it.mediaUri != null }
            if (copies.isNotEmpty()) {
                c.trash.onDeletedForever(copies)
                if (system.isEmpty()) toast(deletedForeverMessage(copies.size))
            }
            if (system.isNotEmpty() && Build.VERSION.SDK_INT >= 30) {
                val request = runCatching { c.deleter.batchRequest(system.map { Uri.parse(it.mediaUri) }) }.getOrNull()
                if (request == null) {
                    c.trash.onDeletedForever(system)
                    toast(deletedForeverMessage(entries.size))
                    return@launch
                }
                pending = Pending.DeleteForever(system)
                _systemRequests.send(request)
            }
        }
    }

    private fun restoredMessage(n: Int) = if (n == 1) "1 musique restaurée" else "$n musiques restaurées"
    private fun deletedForeverMessage(n: Int) = if (n == 1) "1 musique supprimée définitivement" else "$n musiques supprimées définitivement"

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

    fun setCatalogSource(source: CatalogSource) {
        if (_archive.value.source == source) return
        archiveJob?.cancel()
        _archive.update { it.copy(source = source, items = emptyList(), tracks = emptyList(), error = null, searched = false, loading = false, expanded = null) }
        if (_archive.value.query.isNotBlank()) searchArchive()
    }

    fun searchArchive() {
        val state = _archive.value
        val q = state.query.trim()
        if (q.isEmpty()) return
        archiveJob?.cancel()
        archiveJob = viewModelScope.launch {
            _archive.update { it.copy(loading = true, error = null, expanded = null) }
            try {
                when (state.source) {
                    CatalogSource.ARCHIVE -> {
                        val items = c.archive.search(q)
                        _archive.update { it.copy(loading = false, items = items, tracks = emptyList(), searched = true) }
                    }
                    CatalogSource.CCMIXTER -> {
                        val tracks = c.ccMixter.search(q)
                        _archive.update { it.copy(loading = false, tracks = tracks, items = emptyList(), searched = true) }
                    }
                    CatalogSource.JAMENDO -> {
                        val tracks = c.jamendo.search(q, settings.value.jamendoClientId)
                        _archive.update { it.copy(loading = false, tracks = tracks, items = emptyList(), searched = true) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: JamendoRepository.MissingKeyException) {
                _archive.update { it.copy(loading = false, error = "Ajoute d'abord ta clé Jamendo gratuite ci-dessous.", searched = true) }
            } catch (e: Exception) {
                _archive.update { it.copy(loading = false, error = "Recherche impossible. Vérifie ta connexion Internet.", searched = true) }
            }
        }
    }

    fun previewFree(t: FreeTrack) = player.playStream(t.streamUrl, t.title, t.artist)
    fun downloadFree(t: FreeTrack) = download(t.downloadUrl, t.title, t.fileName)
    fun setJamendoClientId(v: String) = c.settings.setJamendoClientId(v)

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
        if (isYoutubeUrl(url)) {
            toast(YOUTUBE_REFUSED)
            return false
        }
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
    fun setAntiSaturation(v: Boolean) = c.settings.setAntiSaturation(v)
    fun setContinueInCalls(v: Boolean) = c.settings.setContinueInCalls(v)
    fun setCallVolume(v: Float) = c.settings.setCallVolume(v)
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

private const val YOUTUBE_REFUSED =
    "Les liens YouTube ne peuvent pas être téléchargés. Utilise la bibliothèque audio YouTube (onglet Télécharger)."
