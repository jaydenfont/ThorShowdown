package com.example.thorshowdown

import android.app.Application
import com.example.thorshowdown.data.ShowdownRepository
import com.example.thorshowdown.data.local.LocalDataSource
import com.example.thorshowdown.data.remote.ShowdownDataSource

/** Holds app-lifetime objects so they outlive any Activity. */
class ThorShowdownApp : Application() {
    val repository by lazy { ShowdownRepository(ShowdownDataSource(), LocalDataSource()) }
}
