package com.example.thorshowdown.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class KeystoreCredentialStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testId = UUID.randomUUID().toString()
    private val keyAlias = KEY_ALIAS_PREFIX + testId
    private val storeFile = File(context.filesDir, STORE_FILE_PREFIX + testId + STORE_FILE_SUFFIX)
    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store =
        KeystoreCredentialStore(
            PreferenceDataStoreFactory.create(scope = storeScope, produceFile = { storeFile }),
            keyAlias,
        )

    @After
    fun cleanUp() {
        storeScope.cancel()
        storeFile.delete()
        androidKeyStore().deleteEntry(keyAlias)
    }

    @Test
    fun KeystoreCredentialStore_NothingSaved_LoadsNull() =
        runBlocking {
            val loaded = store.load()

            assertNull(loaded)
        }

    @Test
    fun KeystoreCredentialStore_SaveThenLoad_ReturnsSavedLogin() =
        runBlocking {
            store.save(LOGIN)

            val loaded = store.load()

            assertEquals(LOGIN, loaded)
        }

    @Test
    fun KeystoreCredentialStore_SaveTwice_LoadsLatestLogin() =
        runBlocking {
            store.save(SavedLogin("Bob", "old-password"))
            store.save(LOGIN)

            val loaded = store.load()

            assertEquals(LOGIN, loaded)
        }

    @Test
    fun KeystoreCredentialStore_Clear_LoadsNull() =
        runBlocking {
            store.save(LOGIN)

            store.clear()

            assertNull(store.load())
        }

    @Test
    fun KeystoreCredentialStore_Save_FileOnDiskDoesNotContainPassword() =
        runBlocking {
            store.save(LOGIN)

            val onDisk = storeFile.readBytes().toString(Charsets.ISO_8859_1)

            assertFalse(LOGIN.password in onDisk)
        }

    @Test
    fun KeystoreCredentialStore_KeyLost_LoadsNullAndForgetsLogin() =
        runBlocking {
            store.save(LOGIN)
            androidKeyStore().deleteEntry(keyAlias)

            val loaded = store.load()

            assertNull(loaded)
            assertFalse(LOGIN.username in storeFile.readBytes().toString(Charsets.ISO_8859_1))
        }

    private fun androidKeyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    private companion object {
        val LOGIN = SavedLogin("Alice", "correct horse battery staple")
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS_PREFIX = "test_credentials_"
        const val STORE_FILE_PREFIX = "test_credentials_"
        const val STORE_FILE_SUFFIX = ".preferences_pb"
    }
}
