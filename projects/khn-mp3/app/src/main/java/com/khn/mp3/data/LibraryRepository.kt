package com.khn.mp3.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.os.Environment
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

/**
 * Bibliothèque locale : fichiers audio du téléphone (MediaStore) + fichiers importés
 * depuis n'importe quel dossier via le sélecteur de documents.
 */
private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "ogg", "opus", "flac", "wav", "wma", "amr", "3gp", "mka")

@OptIn(FlowPreview::class)
class LibraryRepository(
    private val context: Context,
    private val userData: UserDataRepository,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val deviceTracks = MutableStateFlow<List<Track>>(emptyList())

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _hasPermission = MutableStateFlow(hasAudioPermission())
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val collator = Collator.getInstance(Locale.FRENCH).apply { strength = Collator.PRIMARY }

    /** Tous les morceaux, triés par titre. */
    val tracks: StateFlow<List<Track>> = combine(
        deviceTracks,
        userData.data.map { it.imported }.distinctUntilChanged(),
    ) { device, imported ->
        val importedTracks = imported.map { it.toTrack() }
        (device + importedTracks).sortedWith(compareBy(collator) { it.title })
    }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val byId: StateFlow<Map<String, Track>> = tracks
        .map { list -> list.associateBy { it.id } }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    private var observerJob: Job? = null

    init {
        // Re-scan quand le filtre de durée minimale change.
        scope.launch {
            settings.settings.map { it.minDurationSec }.distinctUntilChanged().collect { refresh() }
        }
    }

    fun hasAudioPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    /** À appeler après l'octroi de la permission, ou pour forcer une nouvelle analyse. */
    private val scanMutex = Mutex()

    private val _newTracks = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    /** Nombre de nouveaux titres trouvés lors d'une analyse (pour afficher un message). */
    val newTracks: SharedFlow<Int> = _newTracks.asSharedFlow()

    /** Nouvelle analyse complète : au lancement, à chaque retour dans l'app, ou sur demande. */
    fun refresh() {
        _hasPermission.value = hasAudioPermission()
        if (!_hasPermission.value) return
        startObserving()
        scope.launch {
            indexDownloadFolder()
            scan()
        }
    }

    /**
     * Certains navigateurs n'enregistrent pas leurs téléchargements auprès d'Android :
     * le fichier existe dans Download/ mais reste invisible pour les apps de musique.
     * On demande à Android d'indexer les fichiers audio de ce dossier qu'il ne connaît pas encore.
     */
    private suspend fun indexDownloadFolder() = withContext(Dispatchers.IO) {
        val known = knownDownloadPaths()
        @Suppress("DEPRECATION")
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val missing = runCatching {
            dir.walkTopDown().maxDepth(3)
                .filter { it.isFile && it.extension.lowercase() in AUDIO_EXTENSIONS && it.absolutePath !in known }
                .take(300)
                .map { it.absolutePath }
                .toList()
        }.getOrDefault(emptyList())
        if (missing.isEmpty()) return@withContext
        val remaining = AtomicInteger(missing.size)
        val done = CompletableDeferred<Unit>()
        MediaScannerConnection.scanFile(context, missing.toTypedArray(), null) { _, _ ->
            if (remaining.decrementAndGet() == 0) done.complete(Unit)
        }
        withTimeoutOrNull(8_000) { done.await() }
    }

    @Suppress("DEPRECATION")
    private fun knownDownloadPaths(): Set<String> = runCatching {
        val paths = HashSet<String>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media.DATA),
            "${MediaStore.Audio.Media.DATA} LIKE ?",
            arrayOf("%/Download/%"),
            null,
        )?.use { c -> while (c.moveToNext()) c.getString(0)?.let(paths::add) }
        paths
    }.getOrDefault(emptySet())

    private fun startObserving() {
        if (observerJob != null) return
        observerJob = scope.launch {
            callbackFlow {
                val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) { trySend(Unit) }
                }
                context.contentResolver.registerContentObserver(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer,
                )
                awaitClose { context.contentResolver.unregisterContentObserver(observer) }
            }
                .debounce(1500)
                .collect { scan() }
        }
    }

    private suspend fun scan() = withContext(Dispatchers.IO) {
        scanMutex.withLock { scanLocked() }
    }

    private fun scanLocked() {
        _scanning.value = true
        try {
            val before = deviceTracks.value.mapTo(HashSet()) { it.id }
            val after = queryMediaStore(settings.settings.value.minDurationSec)
            deviceTracks.value = after
            if (before.isNotEmpty()) {
                val added = after.count { it.id !in before }
                if (added > 0) _newTracks.tryEmit(added)
            }
        } catch (_: SecurityException) {
            _hasPermission.value = false
        } finally {
            _scanning.value = false
        }
    }

    private fun queryMediaStore(minDurationSec: Int): List<Track> {
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val folderColumn = if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.RELATIVE_PATH
        else @Suppress("DEPRECATION") MediaStore.Audio.Media.DATA
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DISPLAY_NAME,
            folderColumn,
        )
        val selection = buildString {
            append("(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%')")
            append(" AND ${MediaStore.Audio.Media.IS_NOTIFICATION} = 0")
            append(" AND ${MediaStore.Audio.Media.IS_RINGTONE} = 0")
            append(" AND ${MediaStore.Audio.Media.IS_ALARM} = 0")
            // Durée inconnue (fichier mal indexé) : on garde le titre plutôt que de le masquer.
            append(" AND (${MediaStore.Audio.Media.DURATION} >= ? OR ${MediaStore.Audio.Media.DURATION} IS NULL OR ${MediaStore.Audio.Media.DURATION} = 0)")
        }
        val args = arrayOf((minDurationSec * 1000L).toString())
        val out = ArrayList<Track>(512)
        context.contentResolver.query(collection, projection, selection, args, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val folderCol = c.getColumnIndexOrThrow(folderColumn)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val name = c.getString(nameCol).orEmpty()
                val title = c.getString(titleCol)?.takeIf { it.isNotBlank() } ?: name.substringBeforeLast('.')
                out += Track(
                    id = "ms:$id",
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                    title = title,
                    artist = cleanTag(c.getString(artistCol)) ?: Track.UNKNOWN_ARTIST,
                    album = cleanTag(c.getString(albumCol)) ?: Track.UNKNOWN_ALBUM,
                    durationMs = c.getLong(durCol),
                    dateAdded = c.getLong(dateCol),
                    folder = c.getString(folderCol).orEmpty(),
                    source = Track.Source.DEVICE,
                )
            }
        }
        return out
    }

    private fun cleanTag(v: String?): String? = v?.takeIf { it.isNotBlank() && it != "<unknown>" }

    private fun ImportedTrack.toTrack() = Track(
        id = "doc:$uri",
        uri = Uri.parse(uri),
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        dateAdded = addedAt / 1000,
        folder = "Importés",
        source = Track.Source.IMPORTED,
    )

    /** Importe des fichiers choisis via le sélecteur de documents (accès conservé après redémarrage). */
    suspend fun importDocuments(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        val imported = uris.mapNotNull { uri ->
            // Lecture + écriture si le dossier l'autorise (nécessaire pour pouvoir supprimer le fichier plus tard).
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }.recoverCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            readMetadata(uri)?.let { meta ->
                ImportedTrack(
                    uri = uri.toString(),
                    title = meta.title,
                    artist = meta.artist,
                    album = meta.album,
                    durationMs = meta.durationMs,
                    addedAt = System.currentTimeMillis(),
                )
            }
        }
        userData.addImported(imported)
        imported.size
    }

    data class Meta(val title: String, val artist: String, val album: String, val durationMs: Long)

    /** Métadonnées d'un fichier hors MediaStore (fichier importé ou ouvert depuis une autre app). */
    fun readMetadata(uri: Uri): Meta? {
        val displayName = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Fichier audio"

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            Meta(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?.takeIf { it.isNotBlank() } ?: displayName.substringBeforeLast('.'),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?.takeIf { it.isNotBlank() } ?: Track.UNKNOWN_ARTIST,
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    ?.takeIf { it.isNotBlank() } ?: Track.UNKNOWN_ALBUM,
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L,
            )
        } catch (_: Exception) {
            Meta(displayName.substringBeforeLast('.'), Track.UNKNOWN_ARTIST, Track.UNKNOWN_ALBUM, 0L)
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Morceau temporaire pour un fichier ouvert depuis une autre application. */
    suspend fun externalTrack(uri: Uri): Track = withContext(Dispatchers.IO) {
        val meta = readMetadata(uri) ?: Meta("Fichier audio", Track.UNKNOWN_ARTIST, Track.UNKNOWN_ALBUM, 0)
        Track(
            id = "ext:$uri",
            uri = uri,
            title = meta.title,
            artist = meta.artist,
            album = meta.album,
            durationMs = meta.durationMs,
            dateAdded = System.currentTimeMillis() / 1000,
            folder = "",
            source = Track.Source.EXTERNAL,
        )
    }
}
