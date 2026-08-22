package com.cristianprincipi.habits.data.remote.notion.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [NotionTokenStore] over a DataStore file holding **ciphertext only**.
 *
 * The file is plain on disk — DataStore Preferences offers no encryption of its own, which is
 * exactly why `PreferencesModule`'s KDoc said the token would not live there. What protects it is
 * [TokenCipher]: the bytes written here are AES-GCM output whose key never leaves the Android
 * Keystore, so a copy of the app's data directory yields nothing usable.
 *
 * Base64 because a `Preferences` value is a `String`. It is an encoding, not a protection — the
 * thing it encodes is already ciphertext.
 */
@Singleton
class DataStoreNotionTokenStore
    @Inject
    constructor(
        // `@param:` explicitly, not a bare `@AuthDataStore`. Kotlin warns that a bare annotation
        // on a constructor parameter currently lands on the parameter but will also land on the
        // property in future — and Dagger reads the parameter. Being explicit keeps the qualifier
        // where the injector looks for it whichever way that default goes.
        @param:AuthDataStore private val dataStore: DataStore<Preferences>,
        private val cipher: TokenCipher,
    ) : NotionTokenStore {
        private val storedBlob: Flow<String?> =
            dataStore.data
                .catch { cause ->
                    // Same reasoning as SettingsDataStore: an unreadable file must not take the app
                    // down. Here it means "not connected", and the user reconnects.
                    if (cause is IOException) emit(emptyPreferences()) else throw cause
                }.map { preferences -> preferences[TOKEN] }

        /**
         * True only when a stored blob actually **decrypts**.
         *
         * Deliberately not "a value is present". A blob that survived without its key — after a
         * device transfer, say — would otherwise report connected, and every sync would fail with
         * no explanation the user could act on.
         */
        override val isConnected: Flow<Boolean> =
            storedBlob.map { blob -> blob?.let(::decode)?.let(cipher::decrypt) != null }

        override suspend fun read(): NotionToken? =
            storedBlob
                .first()
                ?.let(::decode)
                ?.let(cipher::decrypt)
                ?.let(::NotionToken)

        override suspend fun save(token: NotionToken) {
            val blob = cipher.encrypt(token.value)
            dataStore.edit { preferences -> preferences[TOKEN] = encode(blob) }
        }

        override suspend fun clear() {
            // Ciphertext first, then the key. In this order an interruption between the two leaves
            // a key with nothing to open — harmless. The reverse leaves ciphertext that is already
            // unreadable but still looks like a stored token to anything reading the file directly.
            dataStore.edit { preferences -> preferences.remove(TOKEN) }
            cipher.invalidate()
        }

        // java.util.Base64, not android.util.Base64: it exists from API 26, which is this
        // project's floor, and it keeps this class free of Android so the store can be unit tested
        // on the JVM. Only the cipher genuinely needs a device.
        private fun encode(blob: ByteArray): String = Base64.getEncoder().encodeToString(blob)

        private fun decode(encoded: String): ByteArray? =
            runCatching { Base64.getDecoder().decode(encoded) }.getOrNull()

        private companion object {
            val TOKEN = stringPreferencesKey("notion_token_ciphertext")
        }
    }
