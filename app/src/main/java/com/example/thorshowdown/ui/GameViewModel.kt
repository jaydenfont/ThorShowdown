package com.example.thorshowdown.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.thorshowdown.ThorShowdownApp
import com.example.thorshowdown.data.LoginState
import com.example.thorshowdown.data.ShowdownRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Top screen (display 0). [loggedInUsername] is null until the native login succeeds. */
data class TopUiState(
    val loggedInUsername: String? = null,
)

/** Bottom screen (display 1). */
data class BottomUiState(
    val login: LoginUiState = LoginUiState.Loading,
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
        val formatId: String,
    ) : UserAction
}

/** Shared by both displays. Turns repository state into per-screen UI state. */
class GameViewModel(
    private val repository: ShowdownRepository,
) : ViewModel() {
    /** Set once the user declines the saved login; UI-only, so it lives here and not in the repository. */
    private val useDifferentAccount = MutableStateFlow(false)

    val topState: StateFlow<TopUiState> =
        repository.loginState
            .map { TopUiState(loggedInUsername = (it as? LoginState.LoggedIn)?.username) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, TopUiState())

    val bottomState: StateFlow<BottomUiState> =
        combine(repository.loginState, useDifferentAccount) { state, declined ->
            BottomUiState(login = LoginUiState.from(state, declined))
        }.stateIn(viewModelScope, SharingStarted.Eagerly, BottomUiState())

    init {
        viewModelScope.launch { repository.loadSavedLogin() }
    }

    fun onAction(action: UserAction) {
        when (action) {
            is UserAction.LogIn -> viewModelScope.launch { repository.login(action.username, action.password) }
            UserAction.ContinueAsSaved -> viewModelScope.launch { repository.loginWithSaved() }
            UserAction.UseDifferentAccount -> useDifferentAccount.value = true
            is UserAction.FindBattle -> viewModelScope.launch { repository.startSearch(action.formatId) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ThorShowdownApp
                    GameViewModel(app.repository)
                }
            }
    }
}
