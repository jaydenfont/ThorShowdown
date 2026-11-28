package com.example.thorshowdown

import android.app.Application
import com.example.thorshowdown.data.ShowdownRepository
import com.example.thorshowdown.data.local.LocalDataSource
import com.example.thorshowdown.data.moves.AssetMoveDex
import com.example.thorshowdown.showdown.DefaultShowdownClient
import com.example.thorshowdown.showdown.ShowdownConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Holds app-lifetime objects so they outlive any Activity. */
class ThorShowdownApp : Application() {
    /** Lives as long as the process; the Showdown connection runs here. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val config = ShowdownConfig()

    private val graph = AppGraph { DefaultAppContainer(this, config) }

    /** Bundled move data, loaded in the background at first use. */
    val moveDex by lazy { AssetMoveDex(assets, applicationScope) }

    val repository by lazy {
        val container = graph.container
        val client = DefaultShowdownClient(config, container.transport, container.loginApi, applicationScope)
        ShowdownRepository(client, LocalDataSource(container.credentialStore), container.webSession, applicationScope)
    }

    /**
     * Replaces the network and device storage, for workflow tests. Must be called before
     * anything uses the repository (that is, before the Activity starts); throws otherwise.
     */
    fun installContainer(container: AppContainer) {
        graph.install(container)
    }
}
