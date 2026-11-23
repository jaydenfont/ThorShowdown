package com.example.thorshowdown.data

import com.example.thorshowdown.data.local.LocalDataSource
import com.example.thorshowdown.data.local.SavedLogin
import com.example.thorshowdown.data.web.WebSession
import com.example.thorshowdown.showdown.LoginFailure
import com.example.thorshowdown.showdown.LoginResult
import com.example.thorshowdown.showdown.ShowdownClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.io.IOException
import java.util.logging.Logger

/**
 * App-scoped single source of truth. Combines the Showdown client and local data.
 * Collects the client's events in [scope] from construction, so none are missed before `connect()`.
 */
class ShowdownRepository(
    private val client: ShowdownClient,
    private val local: LocalDataSource,
    private val webSession: WebSession,
    scope: CoroutineScope,
) {
    private val sessionManager = SessionManager(client)

    /** Every battle the user is in, and which one is shown. */
    val sessions: StateFlow<SessionsState> = sessionManager.state

    private val matchmaker = Matchmaker(client, scope)

    /** The ladder search, if any. */
    val searchState: StateFlow<SearchState> = matchmaker.state

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            client.events.collect { event ->
                matchmaker.handle(event)
                sessionManager.handle(event)
            }
        }
    }

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Loading)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    /** Reads the saved login so it can be offered; doesn't log in. */
    suspend fun loadSavedLogin() {
        _loginState.value = LoginState.LoggedOut(local.savedLogin()?.username)
    }

    /** Held while a login runs, so overlapping attempts can't race each other. */
    private val loginInProgress = Mutex()

    /**
     * Logs in as [username], connecting first if needed. Saves the login when it succeeds.
     * Ignored while another login is running.
     */
    suspend fun login(
        username: String,
        password: String,
    ) {
        if (!loginInProgress.tryLock()) {
            logger.info { "Ignoring login as $username: another login is running" }
            return
        }
        try {
            _loginState.value = LoginState.LoggingIn(username)
            when (val result = connectAndLogin(username, password)) {
                is LoginResult.Success -> onLoggedIn(SavedLogin(username, password), result.username, result.sid)
                is LoginResult.Failure -> onLoginFailed(LoginError(username, result.reason, result.message))
            }
        } finally {
            loginInProgress.unlock()
        }
    }

    /** Logs in with the saved login; stays logged out if there is none. */
    suspend fun loginWithSaved() {
        val saved = local.savedLogin() ?: return
        login(saved.username, saved.password)
    }

    /** Connects (a no-op when already connected), then logs in. A failed connect is a [LoginFailure.Network] failure. */
    private suspend fun connectAndLogin(
        username: String,
        password: String,
    ): LoginResult {
        try {
            client.connect()
        } catch (e: IOException) {
            logger.warning { "Couldn't connect to Showdown: ${e.message}" }
            return LoginResult.Failure(LoginFailure.Network, e.message ?: CONNECT_FAILED_MESSAGE)
        }
        return client.login(username, password)
    }

    /** A refused password makes the saved login for that name useless, so it's forgotten. */
    private suspend fun onLoginFailed(error: LoginError) {
        val saved = local.savedLogin()
        val savedIsRefused = error.reason == LoginFailure.WrongPassword && saved?.username.equals(error.username, ignoreCase = true)
        if (savedIsRefused) local.forgetLogin()
        _loginState.value = LoginState.LoggedOut(saved?.username.takeUnless { savedIsRefused }, error)
    }

    /** Signs the WebView in before reporting the login, so "logged in" means the top screen is ready too. */
    private suspend fun onLoggedIn(
        login: SavedLogin,
        displayName: String,
        sid: String?,
    ) {
        local.saveLogin(login)
        if (sid != null) webSession.signIn(sid) else logger.warning { "Login gave no sid; the top screen will be a spectator" }
        _loginState.value = LoginState.LoggedIn(displayName)
    }

    /** Uses move [slot] (1-based) in battle [roomId] if a choice is awaited. */
    suspend fun chooseMove(
        roomId: String,
        slot: Int,
        terastallize: Boolean,
    ) = sessionManager.chooseMove(roomId, slot, terastallize)

    /** Switches to party [slot] (1-based) in battle [roomId] if a choice is awaited. */
    suspend fun switchTo(
        roomId: String,
        slot: Int,
    ) = sessionManager.switchTo(roomId, slot)

    /** Takes back the choice sent in battle [roomId], if it can still be taken back. */
    suspend fun undo(roomId: String) = sessionManager.undo(roomId)

    suspend fun forfeit(roomId: String) = sessionManager.forfeit(roomId)

    /** Leaves battle [roomId], forfeiting it first if it isn't over. */
    suspend fun leave(roomId: String) = sessionManager.leave(roomId)

    /** Shows battle [roomId]; doesn't leave or forfeit any other. */
    fun selectRoom(roomId: String) = sessionManager.selectRoom(roomId)

    /** Searches the ladder for [format]. Ignored unless logged in, since Showdown refuses guests. */
    suspend fun startSearch(format: BattleFormat) {
        if (_loginState.value !is LoginState.LoggedIn) {
            logger.warning { "Ignoring search for ${format.id}: not logged in" }
            return
        }
        matchmaker.start(format)
    }

    /** Stops the ladder search, if any. */
    suspend fun cancelSearch() = matchmaker.cancel()

    private companion object {
        val logger: Logger = Logger.getLogger(ShowdownRepository::class.java.name)
        const val CONNECT_FAILED_MESSAGE = "Couldn't connect to the Showdown server"
    }
}
