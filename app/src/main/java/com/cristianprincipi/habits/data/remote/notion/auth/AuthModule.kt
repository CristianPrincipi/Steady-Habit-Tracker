package com.cristianprincipi.habits.data.remote.notion.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/** Distinguishes the token's store from the app's settings store — they are different files. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthDataStore

/**
 * The token store's own DataStore file, and the bindings around it.
 *
 * **A separate file from `PreferencesModule`'s, on purpose.** Two reasons, and the second is the
 * one that matters: DataStore permits only one live instance per file (ADR-032), and disconnect has
 * to be able to wipe the token without touching `lastSyncedAt` or anything else the app remembers.
 * Keeping them apart makes that a file boundary rather than a careful `remove` call.
 */
@Module
@InstallIn(SingletonComponent::class)
object AuthDataStoreModule {
    private const val FILE_NAME = "notion_auth"

    @Provides
    @Singleton
    @AuthDataStore
    fun provideAuthDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = createAuthDataStore { context.preferencesDataStoreFile(FILE_NAME) }
}

/**
 * The token store, built the one way the app builds it — M12.7, and `createSettingsDataStore`'s
 * reasoning applies here too: without a corruption handler a `CorruptionException` comes out of
 * every read, which is a crash on launch that repeats forever.
 *
 * ⭐ **Emptying this file is the cheapest loss in the app.** It holds one thing: the encrypted
 * token. Losing it is the state `SyncFailure.NotConnected` already describes, and the ciphertext was
 * useless anyway if the file it lived in was corrupt.
 *
 * ⚠ It does **not** invalidate the Keystore key, and it should not: the key is not the corrupt
 * thing, disconnect is the operation that deliberately destroys both, and doing it here would turn a
 * recoverable file problem into a destroyed key.
 */
internal fun createAuthDataStore(produceFile: () -> java.io.File): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        produceFile = produceFile,
    )

@Module
@InstallIn(SingletonComponent::class)
interface AuthModule {
    @Binds
    @Singleton
    fun bindTokenCipher(impl: KeystoreTokenCipher): TokenCipher

    @Binds
    @Singleton
    fun bindNotionTokenStore(impl: DataStoreNotionTokenStore): NotionTokenStore
}
