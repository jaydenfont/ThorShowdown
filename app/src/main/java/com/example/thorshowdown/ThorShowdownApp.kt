package com.example.thorshowdown

import android.app.Application
import com.example.thorshowdown.data.ShowdownRepository
import com.example.thorshowdown.data.local.LocalDataSource
import com.example.thorshowdown.showdown.DefaultShowdownClient
import com.example.thorshowdown.showdown.OkHttpLoginApi
import com.example.thorshowdown.showdown.OkHttpTransport
import com.example.thorshowdown.showdown.ShowdownConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Holds app-lifetime objects so they outlive any Activity. */
class ThorShowdownApp : Application() {
    /** Lives as long as the process; the Showdown connection runs here. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val repository by lazy {
        val config = ShowdownConfig()
        val client = DefaultShowdownClient(config, OkHttpTransport(config), OkHttpLoginApi(config), applicationScope)
        ShowdownRepository(client, LocalDataSource())
    }
}
