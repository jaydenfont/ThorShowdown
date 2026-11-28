package com.example.thorshowdown

import com.example.thorshowdown.fakes.FakeCredentialStore
import com.example.thorshowdown.fakes.FakeWebSession
import com.example.thorshowdown.showdown.fakes.FakeLoginApi
import com.example.thorshowdown.showdown.fakes.FakeShowdownServer
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class AppGraphTest {
    private val defaultContainer = testContainer()
    private val graph = AppGraph { defaultContainer }

    @Test
    fun AppGraph_NothingInstalled_UsesDefaultContainer() {
        val container = graph.container

        assertSame(defaultContainer, container)
    }

    @Test
    fun AppGraph_InstalledBeforeUse_UsesInstalledContainer() {
        val installed = testContainer()

        graph.install(installed)

        assertSame(installed, graph.container)
    }

    @Test
    fun AppGraph_InstallAfterUse_Throws() {
        graph.container

        assertThrows(IllegalStateException::class.java) { graph.install(testContainer()) }
    }

    private fun testContainer(): AppContainer =
        object : AppContainer {
            override val transport = FakeShowdownServer()
            override val loginApi = FakeLoginApi()
            override val credentialStore = FakeCredentialStore()
            override val webSession = FakeWebSession()
        }
}
