package com.cristianprincipi.habits.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
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
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(FILE_NAME) }
}
