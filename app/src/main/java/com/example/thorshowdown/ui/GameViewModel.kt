package com.example.thorshowdown.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.thorshowdown.ThorShowdownApp
import com.example.thorshowdown.data.BattleFormat
import com.example.thorshowdown.data.BattleSession
import com.example.thorshowdown.data.LoginState
import com.example.thorshowdown.data.SessionsState
import com.example.thorshowdown.data.ShowdownRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.logging.Logger

/**
 * Top screen (display 0). [loggedInUsername] is null until the native login succeeds;
 * [battleRoomId] is the battle the WebView shows, null when idle.
 */
data class TopUiState(
    val loggedInUsername: String? = null,
    val battleRoomId: String? = null,
)

/** Bottom screen (display 1). [lobby] is null until logged in; [battle] is the current battle's menu, if any. */
data class BottomUiState(
    val login: LoginUiState = LoginUiState.Loading,
    val lobby: LobbyUiState? = null,
    val battle: BattleUiState? = null,
)

/** Everything the user can do from either screen. */
sealed interface UserAction {
    data class LogIn(
        val username: String,
        val password: String,
    ) : UserAction

    /** Log in with the saved login. */
    data object ContinueAsSaved : UserAction

    /** Decline the saved login and show the form. */
    data object UseDifferentAccount : UserAction

    data class FindBattle(
        val format: BattleFormat,
    ) : UserAction

    data object CancelSearch : UserAction

    /** Leave battle [roomId], forfeiting it first if it isn't over. */
    data class LeaveBattle(
        val roomId: String,
    ) : UserAction

    /** Use move [slot] (1-based) in the current battle, Terastallizing first if [terastallize]. */
    data class ChooseMove(
        val slot: Int,
        val terastallize: Boolean,
    ) : UserAction

    /** Switch to party [slot] (1-based) in the current battle. */
    data class SwitchTo(
        val slot: Int,
    ) : UserAction

    /** Take back the choice sent in the current battle. */
    data object UndoChoice : UserAction

    /** Forfeit the current battle (it stays open to show the result). */
    data object Forfeit : UserAction
}

/** Shared by both displays. Turns repository state into per-screen UI state. */
class GameViewModel(
    private val repository: ShowdownRepository,
) : ViewModel() {
    /** Set once the user declines the saved login; UI-only, so it lives here and not in the repository. */
    private val useDifferentAccount = MutableStateFlow(false)

    val topState: StateFlow<TopUiState> =
        combine(repository.loginState, repository.sessions) { login, sessions ->
            val username = (login as? LoginState.LoggedIn)?.username
            TopUiState(loggedInUsername = username, battleRoomId = sessions.currentRoomId.takeIf { username != null })
        }.stateIn(viewModelScope, SharingStarted.Eagerly, TopUiState())

    val bottomState: StateFlow<BottomUiState> =
        combine(
            repository.loginState,
            useDifferentAccount,
            repository.searchState,
            repository.sessions,
        ) { state, declined, search, sessions ->
            BottomUiState(
                login = LoginUiState.from(state, declined),
                lobby = if (state is LoginState.LoggedIn) LobbyUiState(search) else null,
                battle = sessions.current()?.let(BattleUiState::from).takeIf { state is LoginState.LoggedIn },
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, BottomUiState())

    init {
        viewModelScope.launch { repository.loadSavedLogin() }
    }

    fun onAction(action: UserAction) {
        when (action) {
            is UserAction.LogIn -> viewModelScope.launch { repository.login(action.username, action.password) }
            UserAction.ContinueAsSaved -> viewModelScope.launch { repository.loginWithSaved() }
            UserAction.UseDifferentAccount -> useDifferentAccount.value = true
            is UserAction.FindBattle -> viewModelScope.launch { repository.startSearch(action.format) }
            UserAction.CancelSearch -> viewModelScope.launch { repository.cancelSearch() }
            is UserAction.LeaveBattle -> viewModelScope.launch { repository.leave(action.roomId) }
            is UserAction.ChooseMove -> inCurrentBattle { repository.chooseMove(it, action.slot, action.terastallize) }
            is UserAction.SwitchTo -> inCurrentBattle { repository.switchTo(it, action.slot) }
            UserAction.UndoChoice -> inCurrentBattle { repository.undo(it) }
            UserAction.Forfeit -> inCurrentBattle { repository.forfeit(it) }
        }
    }

    /** Runs [command] for the battle being shown; logged and ignored when there is none. */
    private fun inCurrentBattle(command: suspend (roomId: String) -> Unit) {
        val roomId = repository.sessions.value.currentRoomId
        if (roomId == null) {
            logger.info("Ignoring a battle action: no battle is shown")
            return
        }
        viewModelScope.launch { command(roomId) }
    }

    private fun SessionsState.current(): BattleSession? = currentRoomId?.let(sessions::get)

    companion object {
        private val logger: Logger = Logger.getLogger(GameViewModel::class.java.name)

        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ThorShowdownApp
                    GameViewModel(app.repository)
                }
            }
    }
}
