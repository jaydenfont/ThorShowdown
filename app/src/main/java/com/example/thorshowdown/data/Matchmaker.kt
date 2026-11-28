package com.example.thorshowdown.data

import com.example.thorshowdown.showdown.NotConnectedException
import com.example.thorshowdown.showdown.ShowdownClient
import com.example.thorshowdown.showdown.ShowdownEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.logging.Logger
import kotlin.time.Duration.Companion.seconds

/**
 * Runs ladder searches: sends the search commands and tracks [state] from the server's replies.
 * A search the server neither confirms nor refuses within 10 seconds ends as [SearchError.NoResponse];
 * the timer runs in [scope].
 */
class Matchmaker(
    private val client: ShowdownClient,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<SearchState>(SearchState.Idle())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var timeoutJob: Job? = null

    /** Updates [state] from a server event. */
    fun handle(event: ShowdownEvent) {
        when (event) {
            is ShowdownEvent.UpdateSearch -> onUpdateSearch(event.searching)
            is ShowdownEvent.Popup -> onPopup(event.message)
            is ShowdownEvent.PrivateMessage -> if (event.from == ShowdownEvent.PrivateMessage.SYSTEM_SENDER) onServerMessage(event.message)
            is ShowdownEvent.Disconnected -> _state.value = SearchState.Idle()
            else -> Unit
        }
    }

    /** Searches the ladder for [format] without a team. Ignored unless idle. */
    suspend fun start(format: BattleFormat) {
        if (!claimSearch(format)) {
            logger.info { "Ignoring search for ${format.id}: already ${_state.value}" }
            return
        }
        armTimeout()
        send(SEARCH_ACTION) {
            client.useTeam(packedTeam = null)
            client.search(format.id)
        }
    }

    /** Stops the search and shows idle at once; a late `|updatesearch|` corrects it. A no-op when idle. */
    suspend fun cancel() {
        if (_state.value is SearchState.Idle) {
            logger.info { "Ignoring cancel: not searching" }
            return
        }
        timeoutJob?.cancel()
        _state.value = SearchState.Idle()
        send(CANCEL_ACTION) { client.cancelSearch() }
    }

    /** Moves Idle → Requested atomically, so two quick taps can't both send a search. */
    private fun claimSearch(format: BattleFormat): Boolean {
        var claimed = false
        _state.update { current ->
            claimed = current is SearchState.Idle
            if (claimed) SearchState.Requested(format) else current
        }
        return claimed
    }

    /** Replaces any earlier timer, so an old search's timeout can't end a retry. */
    private fun armTimeout() {
        timeoutJob?.cancel()
        timeoutJob =
            scope.launch {
                delay(SEARCH_TIMEOUT)
                _state.update { current -> if (current is SearchState.Requested) SearchState.Idle(SearchError.NoResponse) else current }
            }
    }

    /** Runs [command]; a dead connection ends the search as [SearchError.SendFailed]. */
    private suspend fun send(
        action: String,
        command: suspend () -> Unit,
    ) {
        try {
            command()
        } catch (e: IOException) {
            onSendFailed(action, e)
        } catch (e: NotConnectedException) {
            onSendFailed(action, e)
        }
    }

    private fun onSendFailed(
        action: String,
        cause: Exception,
    ) {
        logger.warning { "Couldn't send $action: ${cause.message}" }
        timeoutJob?.cancel()
        _state.value = SearchState.Idle(SearchError.SendFailed)
    }

    /** The server lists what it's searching; anything else (cancelled, matched, dropped) means idle. */
    private fun onUpdateSearch(searching: List<String>) {
        val format = BattleFormat.entries.firstOrNull { it.id in searching }
        _state.value = format?.let { SearchState.Searching(it) } ?: SearchState.Idle()
    }

    /** A server message right after a search, before it's confirmed, is the server refusing it. */
    private fun onServerMessage(message: String) {
        val refusal = SearchState.Idle(SearchError.Refused(readable(message)))
        _state.update { current -> if (current is SearchState.Requested) refusal else current }
    }

    /**
     * Popups can also stop a confirmed search without a later `|updatesearch|` (e.g. the matched
     * opponent went offline), so any popup while searching ends it and shows why.
     */
    private fun onPopup(message: String) {
        onServerMessage(message)
        val interruption = SearchState.Idle(SearchError.Interrupted(readable(message)))
        _state.update { current -> if (current is SearchState.Searching) interruption else current }
    }

    private fun readable(message: String): String = message.replace(POPUP_LINE_BREAK, LINE_BREAK)

    private companion object {
        val logger: Logger = Logger.getLogger(Matchmaker::class.java.name)
        val SEARCH_TIMEOUT = 10.seconds
        const val SEARCH_ACTION = "search"
        const val CANCEL_ACTION = "cancel search"

        /** Showdown marks line breaks in popups with `||`. */
        const val POPUP_LINE_BREAK = "||"
        const val LINE_BREAK = "\n"
    }
}
