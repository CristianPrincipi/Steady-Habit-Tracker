package com.cristianprincipi.habits.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The settings store against a real file, on the platform the app actually ships to.
 *
 * `SettingsDataStoreTest` covers the same contract on the JVM over an in-memory store, and says why
 * it cannot use a file: DataStore renames a temporary file over the target, and `File.renameTo` on
 * Windows will not overwrite. Android is where that rename works, so this is where the value is
 * proved to survive DataStore's own serialisation — and, more to the point, to survive the store
 * being closed and reopened, which no in-memory double can say anything about.
 */
@RunWith(AndroidJUnit4::class)
class SettingsDataStoreRealFileTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var file: File
    private var openScope: CoroutineScope? = null

    @Before
    fun setUp() {
        // Not preferencesDataStoreFile("settings") — that is the real app's store, and a test has
        // no business writing to it.
        file = File(context.cacheDir, "settings_test_${System.nanoTime()}.preferences_pb")
    }

    @After
    fun tearDown() {
        closeStore()
        file.delete()
    }

    /**
     * Opens a store over the file, closing any previous one first.
     *
     * DataStore permits **one live instance per file** and throws if a second is created while the
     * first is alive, so reopening means releasing the scope that owns the first. That constraint is
     * why `PreferencesModule` provides the real store as a `@Singleton`.
     */
    private fun openStore(): SettingsDataStore {
        closeStore()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        openScope = scope
        return SettingsDataStore(PreferenceDataStoreFactory.create(scope = scope) { file })
    }

    private fun closeStore() {
        openScope?.cancel()
        openScope = null
    }

    @Test
    fun aFirstRunWithNoFileYetHasNeverSynced() =
        runTest {
            assertNull(openStore().lastSyncedAtEpochMillis.first())
        }

    @Test
    fun aSyncTimeSurvivesDataStoresSerialisation() =
        runTest {
            val settings = openStore()

            settings.setLastSyncedAtEpochMillis(1_755_000_000_000L)

            assertEquals(1_755_000_000_000L, settings.lastSyncedAtEpochMillis.first())
        }

    @Test
    fun theLatestSyncReplacesTheOneBeforeIt() =
        runTest {
            // The write path the JVM cannot run: a second write, renaming over a file that already
            // exists.
            val settings = openStore()

            settings.setLastSyncedAtEpochMillis(1L)
            settings.setLastSyncedAtEpochMillis(1_755_000_000_000L)

            assertEquals(1_755_000_000_000L, settings.lastSyncedAtEpochMillis.first())
        }

    @Test
    fun aSyncTimeSurvivesTheStoreThatWroteIt() =
        runTest {
            // What the value is FOR: telling the user when their data was last actually correct,
            // after they closed the app and came back.
            openStore().setLastSyncedAtEpochMillis(1_755_000_000_000L)

            val reopened = openStore()

            assertEquals(1_755_000_000_000L, reopened.lastSyncedAtEpochMillis.first())
        }
}
