package com.example.thorshowdown.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import java.util.logging.Logger
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * [CredentialStore] in DataStore. The username is stored as is; the password is encrypted with
 * AES-256-GCM under an Android Keystore key ([keyAlias]) that never leaves the Keystore.
 *
 * A password that can't be decrypted (e.g. the key was lost after a restore to another device)
 * makes the saved login unusable, so it's cleared and reported as absent.
 */
class KeystoreCredentialStore(
    private val dataStore: DataStore<Preferences>,
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
) : CredentialStore {
    override suspend fun load(): SavedLogin? {
        val preferences = dataStore.data.first()
        val username = preferences[USERNAME_KEY] ?: return null
        val encryptedPassword = preferences[ENCRYPTED_PASSWORD_KEY] ?: return null
        val password = withContext(Dispatchers.IO) { decryptOrNull(encryptedPassword) }
        if (password == null) {
            logger.warning { "Saved password for $username can't be decrypted; forgetting the saved login" }
            clear()
            return null
        }
        return SavedLogin(username, password)
    }

    override suspend fun save(login: SavedLogin) {
        val encryptedPassword = withContext(Dispatchers.IO) { encrypt(login.password) }
        dataStore.edit {
            it[USERNAME_KEY] = login.username
            it[ENCRYPTED_PASSWORD_KEY] = encryptedPassword
        }
        logger.info { "Saved login for ${login.username}" }
    }

    override suspend fun clear() {
        dataStore.edit {
            it.remove(USERNAME_KEY)
            it.remove(ENCRYPTED_PASSWORD_KEY)
        }
    }

    /** Returns Base64(IV ‖ ciphertext). */
    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, existingKey() ?: generateKey())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(cipher.iv + ciphertext)
    }

    /** Null if [encoded] is malformed, the key is gone, or authentication fails. */
    private fun decryptOrNull(encoded: String): String? {
        val key = existingKey() ?: return null
        return try {
            val bytes = Base64.getDecoder().decode(encoded)
            if (bytes.size <= GCM_IV_BYTES) return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, bytes, 0, GCM_IV_BYTES))
            cipher.doFinal(bytes, GCM_IV_BYTES, bytes.size - GCM_IV_BYTES).toString(Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            logger.warning { "Decrypting the saved password failed: ${e.javaClass.simpleName}" }
            null
        } catch (e: IllegalArgumentException) {
            logger.warning { "Saved password isn't valid Base64" }
            null
        }
    }

    private fun existingKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        return keyStore.getKey(keyAlias, null) as SecretKey?
    }

    private fun generateKey(): SecretKey {
        val spec =
            KeyGenParameterSpec
                .Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build()
        return KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            .apply { init(spec) }
            .generateKey()
    }

    companion object {
        const val DEFAULT_KEY_ALIAS = "thorshowdown_credentials"

        private val logger: Logger = Logger.getLogger(KeystoreCredentialStore::class.java.name)
        private val USERNAME_KEY = stringPreferencesKey("username")
        private val ENCRYPTED_PASSWORD_KEY = stringPreferencesKey("encrypted_password")
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
    }
}
