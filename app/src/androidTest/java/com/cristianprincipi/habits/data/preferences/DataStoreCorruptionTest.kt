package com.cristianprincipi.habits.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.data.remote.notion.auth.createAuthDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A corrupt preferences file, on the platform it would actually happen on — M12.7.
 *
 * ### ⚠⚠ What this pins is a crash that never stops
 *
 * DataStore throws `CorruptionException` out of **every read** of a file it cannot parse, and every
 * read in this app is a `Flow` a ViewModel collects through `stateIn`. Without a handler that is not
 * a bad screen or a lost setting: it is a crash on launch, on every launch, with no way out but
 * clearing the app's data — which a user has no reason to think of and nothing tells them to do.
 *
 * ### Why it calls the app's own factories
 *
 * `createSettingsDataStore` and `createAuthDataStore` exist so this test can build the store **the
 * way the app builds it**. A test that assembled its own `PreferenceDataStoreFactory` would pass
 * whether or not the shipped modules had a handler at all — which is precisely the class of defect
 * this whole step is sweeping for.
 *
 * ### Why instrumented rather than JVM
 *
 * `SettingsDataStoreRealFileTest` says it: DataStore renames a temporary file over the target, and
 * `File.renameTo` on Windows will not overwrite. Android is where that rename works.
 */
@RunWith(AndroidJUnit4::class)
class DataStoreCorruptionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var file: File

    @Before
    fun setUp() {
        // Never the real store's file — a test has no business writing to the app's own settings.
        file = File(context.cacheDir, "corrupt_test_${System.nanoTime()}.preferences_pb")
    }

    @After
    fun tearDown() {
        file.delete()
    }

    /** Bytes that are definitively not a preferences proto. */
    private fun writeGarbage() {
        file.writeBytes(ByteArray(64) { 0xFF.toByte() })
    }

    @Test
    fun aCorruptSettingsFileReadsAsEmptyRatherThanThrowing() =
        runTest {
            writeGarbage()

            val store = createSettingsDataStore { file }

            assertEquals(emptyPreferences(), store.data.first())
        }

    /**
     * ⭐ The token store gets the same treatment, and losing it costs the least of anything here.
     *
     * The file holds one value — the encrypted token — and the state after this is the one
     * `SyncFailure.NotConnected` already describes. The ciphertext was unreadable anyway.
     */
    @Test
    fun aCorruptTokenFileReadsAsEmptyRatherThanThrowing() =
        runTest {
            writeGarbage()

            val store = createAuthDataStore { file }

            assertEquals(emptyPreferences(), store.data.first())
        }

    /**
     * ⚠ The half that says the app is usable afterwards, not merely un-crashed.
     *
     * Replacing the file is only the right trade if the store keeps working once it has: an app that
     * survives launch and then cannot save a mapping would have moved the crash rather than removed
     * it.
     */
    @Test
    fun aReplacedFileIsAWorkingStoreAndNotAWreck() =
        runTest {
            writeGarbage()

            val store = createSettingsDataStore { file }
            val settings = SettingsDataStore(store)
            settings.setLastSyncedAtEpochMillis(1_700_000_000_000)

            assertEquals(1_700_000_000_000, settings.lastSyncedAtEpochMillis.first())
        }
}
