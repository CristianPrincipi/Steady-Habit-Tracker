package com.cristianprincipi.habits.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Builds the one [DataStore] this app has.
 *
 * **`@Singleton` is a correctness requirement here, not a performance one.** DataStore takes an
 * exclusive lock on its file per process, and a second instance over the same file throws rather
 * than racing. Hilt owning exactly one is what makes that impossible to get wrong — which is also
 * why the file name is not exposed: nothing else should be able to open it.
 *
 * **The token never comes near this.** `CLAUDE.md` §3.6 requires the Notion integration token to be
 * encrypted under the Android Keystore, and DataStore Preferences is plaintext XML-equivalent on
 * disk. The token store is a separate thing built in M4 under `data/remote/notion/auth`.
 */
@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {
    private const val FILE_NAME = "settings"

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = createSettingsDataStore { context.preferencesDataStoreFile(FILE_NAME) }
}

/**
 * The settings store, built the one way the app builds it — M12.7.
 *
 * **A function rather than a line inside the `@Provides`, so a test can prove the shipped
 * construction.** A corruption test that assembled its own factory would pass whether or not the
 * app had a handler at all, which is the failure mode this whole step is about.
 *
 * ### ⚠⚠ Why there is a corruption handler at all
 *
 * Without one, a `CorruptionException` propagates out of **every read**, and every read here is a
 * `Flow` a ViewModel collects through `stateIn`. That is not a bad screen: it is a crash on launch
 * that repeats on every launch, with no way out but clearing the app's data — which a user has no
 * reason to think of and no prompt telling them to.
 *
 * ### What replacing the file costs, said plainly
 *
 * This store holds the property mapping, the last sync time, the last failure and the
 * discarded-edit notices. Emptying it makes the app **forget its Notion setup**, so `HabitsShell`'s
 * gate flips and the connection wizard comes back — visible, explicable, and recoverable in a
 * minute. ⭐ **Room is untouched**, so the habits and every day of history survive; they are
 * re-attached when the mapping is set up again (ADR-081 restores a returning column in place).
 *
 * Losing a mapping is a real cost. It is the smaller one: the alternative is an app that never
 * opens again.
 */
internal fun createSettingsDataStore(produceFile: () -> java.io.File): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        produceFile = produceFile,
    )
