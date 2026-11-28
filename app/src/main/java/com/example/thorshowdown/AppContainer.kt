package com.example.thorshowdown

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.example.thorshowdown.data.local.CredentialStore
import com.example.thorshowdown.data.local.KeystoreCredentialStore
import com.example.thorshowdown.data.web.CookieManagerWebSession
import com.example.thorshowdown.data.web.WebSession
import com.example.thorshowdown.showdown.LoginApi
import com.example.thorshowdown.showdown.OkHttpLoginApi
import com.example.thorshowdown.showdown.OkHttpTransport
import com.example.thorshowdown.showdown.ShowdownConfig
import com.example.thorshowdown.showdown.Transport

/** DataStore file holding the saved login; excluded from backups (see res/xml). */
private const val CREDENTIALS_STORE_NAME = "credentials"

private val Context.credentialsDataStore: DataStore<Preferences> by preferencesDataStore(CREDENTIALS_STORE_NAME)

/**
 * What the network and the device provide to the app. Everything above these (client,
 * repository, ViewModel, UI) is real in every build; workflow tests swap only these.
 */
interface AppContainer {
    val transport: Transport
    val loginApi: LoginApi
    val credentialStore: CredentialStore
    val webSession: WebSession
}

/** The real network and device storage. */
class DefaultAppContainer(
    context: Context,
    config: ShowdownConfig,
) : AppContainer {
    override val transport: Transport = OkHttpTransport(config)
    override val loginApi: LoginApi = OkHttpLoginApi(config)
    override val credentialStore: CredentialStore = KeystoreCredentialStore(context.credentialsDataStore)
    override val webSession: WebSession = CookieManagerWebSession()
}
