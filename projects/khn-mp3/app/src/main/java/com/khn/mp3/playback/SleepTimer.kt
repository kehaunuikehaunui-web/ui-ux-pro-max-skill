package com.khn.mp3.playback

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Minuterie de mise en veille : pause après un délai ou à la fin du morceau en cours. */
class SleepTimer {
    @Immutable
    sealed interface State {
        data object Off : State
        data class At(val endAtMs: Long) : State
        data object EndOfTrack : State
    }

    private val _state = MutableStateFlow<State>(State.Off)
    val state: StateFlow<State> = _state.asStateFlow()

    fun start(minutes: Int) { _state.value = State.At(System.currentTimeMillis() + minutes * 60_000L) }
    fun endOfTrack() { _state.value = State.EndOfTrack }
    fun cancel() { _state.value = State.Off }
}
