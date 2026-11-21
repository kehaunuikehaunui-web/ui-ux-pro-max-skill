package com.khn.mp3

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.core.content.IntentCompat
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.khn.mp3.ui.KhnAppRoot
import com.khn.mp3.ui.MainViewModel
import com.khn.mp3.ui.theme.KhnTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private val openPlayer = Channel<Unit>(Channel.CONFLATED)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            KhnTheme {
                KhnAppRoot(openPlayerEvents = openPlayer.receiveAsFlow(), vm = vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        container.player.connect()
        // Retour dans l'app (ex. après un téléchargement dans le navigateur) : nouvelle analyse.
        container.library.refresh()
    }

    override fun onStop() {
        // La lecture continue dans PlaybackService ; on libère seulement le contrôleur de l'UI.
        container.player.disconnect()
        super.onStop()
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data?.let { uri ->
                vm.playExternal(uri)
                openPlayer.trySend(Unit)
            }
            ACTION_OPEN_PLAYER -> openPlayer.trySend(Unit)
            Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("audio/") == true) {
                    IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                        ?.let { vm.importShared(listOf(it)) }
                } else {
                    intent.getStringExtra(Intent.EXTRA_TEXT)?.let(vm::handleSharedText)
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?.let { vm.importShared(it) }
            }
        }
    }

    companion object {
        const val ACTION_OPEN_PLAYER = "com.khn.mp3.OPEN_PLAYER"
    }
}
