package com.example.thorshowdown

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.example.thorshowdown.data.ShowdownRepository
import com.example.thorshowdown.data.local.KeystoreCredentialStore
import com.example.thorshowdown.data.local.LocalDataSource
import com.example.thorshowdown.showdown.DefaultShowdownClient
import com.example.thorshowdown.showdown.OkHttpLoginApi
import com.example.thorshowdown.showdown.OkHttpTransport
import com.example.thorshowdown.showdown.ShowdownConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** DataStore file holding the saved login; excluded from backups (see res/xml). */
private const val CREDENTIALS_STORE_NAME = "credentials"

private val Context.credentialsDataStore: DataStore<Preferences> by preferencesDataStore(CREDENTIALS_STORE_NAME)

/** Holds app-lifetime objects so they outlive any Activity. */
class ThorShowdownApp : Application() {
    /** Lives as long as the process; the Showdown connection runs here. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val repository by lazy {
        val config = ShowdownConfig()
        val client = DefaultShowdownClient(config, OkHttpTransport(config), OkHttpLoginApi(config), applicationScope)
        val local = LocalDataSource(KeystoreCredentialStore(credentialsDataStore))
        ShowdownRepository(client, local)
    }
}
