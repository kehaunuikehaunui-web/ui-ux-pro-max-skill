package com.khn.mp3

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import com.khn.mp3.data.ArchiveRepository
import com.khn.mp3.data.CcMixterRepository
import com.khn.mp3.data.JamendoRepository
import com.khn.mp3.data.SharedAudioImporter
import com.khn.mp3.data.TrackDeleter
import com.khn.mp3.data.DownloadRepository
import com.khn.mp3.data.LibraryRepository
import com.khn.mp3.data.SettingsRepository
import com.khn.mp3.data.UserDataRepository
import com.khn.mp3.playback.AudioEffects
import com.khn.mp3.playback.PlayerController
import com.khn.mp3.ui.components.ArtworkFetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class KhnApp : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            add(ArtworkFetcher.Factory(this@KhnApp))
            add(ArtworkFetcher.Key())
        }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
        .crossfade(120)
        .allowRgb565(true)
        .build()
}

/** Dépendances de l'app (injection manuelle, pas de framework). */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsRepository(app, scope)
    val userData = UserDataRepository(app, scope)
    val library = LibraryRepository(app, userData, settings, scope)
    val archive = ArchiveRepository()
    val ccMixter = CcMixterRepository()
    val jamendo = JamendoRepository()
    val sharedImporter = SharedAudioImporter(app)
    val deleter = TrackDeleter(app)
    val downloads = DownloadRepository(app, userData)
    val audioEffects = AudioEffects(settings, scope)
    val player = PlayerController(app)
}

val Context.container: AppContainer get() = (applicationContext as KhnApp).container
