package com.nuvio.app.features.sports

import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.features.sports.ui.SportsActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * "Watch" on a game. The stream is looked up for this game alone if the background round has not got
 * to it yet, so pressing Play never waits on the rest of the day's matching.
 */
object SportsWatch {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _finding = MutableStateFlow<Set<String>>(emptySet())

    /** Ids of the games being looked up right now (their Play buttons show a spinner). */
    val finding: StateFlow<Set<String>> = _finding.asStateFlow()

    /** Starts looking in the background so a later press is instant. */
    fun warm(ev: SportsEvent) {
        if (ev.state == GameState.POST || ev.state == GameState.OFF) return
        if (SportsRepository.matches.value[ev.id].orEmpty().isNotEmpty()) return
        scope.launch { runCatching { SportsRepository.matchNow(ev) } }
    }

    /**
     * Plays the best stream, or, with "let me choose", hands the ordered list to [onChoose]
     * (or opens the game's page when there is nowhere to show a list).
     */
    internal fun watch(ev: SportsEvent, actions: SportsActions, onChoose: ((List<ChannelMatch>) -> Unit)? = null) {
        if (ev.id in _finding.value) return
        scope.launch {
            var list = SportsRepository.matches.value[ev.id].orEmpty()
            if (list.isEmpty()) {
                _finding.update { it + ev.id }
                list = try {
                    runCatching { SportsRepository.matchNow(ev) }.getOrDefault(emptyList())
                } finally {
                    _finding.update { it - ev.id }
                }
            }
            val settings = SportsRepository.settings.value
            val ordered = SportsStreams.ordered(list, settings)
            when {
                ordered.isEmpty() -> NuvioToastController.show(if (SportsRepository.channelsReady) "No stream found for this game yet" else "Your channels are still loading, try again in a moment")
                settings.choosePlayStream && ordered.size > 1 -> if (onChoose != null) onChoose(ordered) else actions.onOpenGame(ev)
                else -> actions.onPlay(ordered.first().channel, ev.title)
            }
        }
    }
}
