package com.khn.mp3.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Favoris, playlists, fichiers importés et historique de téléchargements.
 * Persistés en JSON (écriture atomique) dans le stockage privé de l'app.
 */
class UserDataRepository(context: Context, private val scope: CoroutineScope) {

    private val file = AtomicFile(File(context.filesDir, "userdata.json"))
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val writeLock = Mutex()

    private val _data = MutableStateFlow(UserData())
    val data: StateFlow<UserData> = _data.asStateFlow()

    val favorites: StateFlow<Set<String>> = _data
        .map { it.favorites.toSet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            val loadedData = runCatching {
                if (file.baseFile.exists()) json.decodeFromString<UserData>(file.readFully().decodeToString()) else null
            }.getOrNull()
            if (loadedData != null) _data.value = loadedData
            _loaded.value = true
        }
    }

    private fun mutate(transform: (UserData) -> UserData) {
        _data.update(transform)
        persist()
    }

    private fun persist() {
        // Ne jamais écraser le fichier avant de l'avoir relu.
        if (!_loaded.value) return
        scope.launch(Dispatchers.IO) {
            writeLock.withLock {
                val snapshot = _data.value
                val stream = file.startWrite()
                try {
                    stream.write(json.encodeToString(UserData.serializer(), snapshot).encodeToByteArray())
                    file.finishWrite(stream)
                } catch (t: Throwable) {
                    file.failWrite(stream)
                }
            }
        }
    }

    // ---- Favoris ----
    fun toggleFavorite(trackId: String) = mutate { d ->
        if (trackId in d.favorites) d.copy(favorites = d.favorites - trackId)
        else d.copy(favorites = listOf(trackId) + d.favorites)
    }

    // ---- Playlists ----
    fun createPlaylist(name: String, initial: List<String> = emptyList()): String {
        val id = UUID.randomUUID().toString()
        mutate { d -> d.copy(playlists = d.playlists + Playlist(id, name.trim().ifEmpty { "Ma playlist" }, initial.distinct())) }
        return id
    }

    fun renamePlaylist(id: String, name: String) = mutate { d ->
        d.copy(playlists = d.playlists.map { if (it.id == id) it.copy(name = name.trim().ifEmpty { it.name }) else it })
    }

    fun deletePlaylist(id: String) = mutate { d -> d.copy(playlists = d.playlists.filterNot { it.id == id }) }

    fun addToPlaylist(id: String, trackIds: List<String>) = mutate { d ->
        d.copy(playlists = d.playlists.map { p ->
            if (p.id == id) p.copy(trackIds = p.trackIds + trackIds.filterNot { it in p.trackIds }) else p
        })
    }

    fun removeFromPlaylist(id: String, trackId: String) = mutate { d ->
        d.copy(playlists = d.playlists.map { p -> if (p.id == id) p.copy(trackIds = p.trackIds - trackId) else p })
    }

    fun movePlaylistItem(id: String, from: Int, to: Int) = mutate { d ->
        d.copy(playlists = d.playlists.map { p ->
            if (p.id != id || from !in p.trackIds.indices || to !in p.trackIds.indices) p
            else p.copy(trackIds = p.trackIds.toMutableList().apply { add(to, removeAt(from)) })
        })
    }

    /** Après suppression de fichiers : on les retire des favoris, playlists et fichiers importés. */
    fun forgetTracks(trackIds: Set<String>) = mutate { d ->
        d.copy(
            favorites = d.favorites.filterNot { it in trackIds },
            playlists = d.playlists.map { p -> p.copy(trackIds = p.trackIds.filterNot { it in trackIds }) },
            imported = d.imported.filterNot { "doc:${it.uri}" in trackIds },
        )
    }

    // ---- Fichiers importés ----
    fun addImported(tracks: List<ImportedTrack>) = mutate { d ->
        val known = d.imported.map { it.uri }.toSet()
        d.copy(imported = d.imported + tracks.filterNot { it.uri in known })
    }

    fun removeImported(uri: String) = mutate { d -> d.copy(imported = d.imported.filterNot { it.uri == uri }) }

    // ---- Téléchargements ----
    fun addDownload(entry: DownloadEntry) = mutate { d -> d.copy(downloads = (listOf(entry) + d.downloads).take(50)) }

    fun clearDownloads() = mutate { d -> d.copy(downloads = emptyList()) }
}
