package com.cristianprincipi.habits.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.cristianprincipi.habits.data.repository.SettingsRepositoryImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import java.time.Instant

/**
 * The settings store and its repository, over an in-memory [DataStore].
 *
 * **No filesystem here, deliberately.** DataStore writes a temporary file and renames it over the
 * target, and `File.renameTo` on Windows refuses to overwrite an existing file — so a real-file
 * DataStore on this machine accepts exactly one write and fails the second with a misleading
 * "multiple instances of DataStore for this file". That is a JVM-on-Windows limitation rather than
 * anything the app will meet on Android, and a test that passed only because it happened to write
 * once would be a trap for whoever added the second write.
 *
 * The real file is therefore proved where the platform matches production, in
 * `SettingsDataStoreRealFileTest` under `androidTest`. What is left here is everything that is
 * genuinely ours: the key, the null-means-never contract, the mapping to [Instant], and the two
 * failure paths.
 */
class SettingsDataStoreTest {
    @Test
    fun aFirstRunHasNeverSynced() =
        runTest {
            // Null, not zero. Zero is a real instant — 1970 — and the Settings screen would render
            // it as a date rather than as "never".
            assertNull(SettingsDataStore(InMemoryDataStore()).lastSyncedAtEpochMillis.first())
        }

    @Test
    fun aSyncTimeIsReadBackUnderTheSameKeyItWasWrittenTo() =
        runTest {
            val settings = SettingsDataStore(InMemoryDataStore())

            settings.setLastSyncedAtEpochMillis(1_755_000_000_000L)

            assertEquals(1_755_000_000_000L, settings.lastSyncedAtEpochMillis.first())
        }

    @Test
    fun theLatestSyncReplacesTheOneBeforeIt() =
        runTest {
            val settings = SettingsDataStore(InMemoryDataStore())

            settings.setLastSyncedAtEpochMillis(1L)
            settings.setLastSyncedAtEpochMillis(2L)

            assertEquals(2L, settings.lastSyncedAtEpochMillis.first())
        }

    @Test
    fun anUnreadableFileReadsAsNeverSyncedInsteadOfThrowing() =
        runTest {
            // DataStore reports a missing or corrupt file as an IOException on the flow. This value
            // drives one caption on the Settings screen and the collector M4 hangs its scheduling
            // off; taking either down over it would trade a wrong caption for a crash.
            val settings = SettingsDataStore(FailingDataStore(IOException("corrupt")))

            assertNull(settings.lastSyncedAtEpochMillis.first())
        }

    @Test
    fun aFailureThatIsNotIoStillPropagates() =
        runTest {
            // The catch is narrow on purpose: anything that is not an IOException is a real bug and
            // must not be swallowed into a plausible-looking "never synced".
            val settings = SettingsDataStore(FailingDataStore(IllegalStateException("a real bug")))

            val thrown = runCatching { settings.lastSyncedAtEpochMillis.first() }.exceptionOrNull()

            assertEquals("a real bug", thrown?.message)
        }

    @Test
    fun theRepositoryHandsTheDomainAnInstant() =
        runTest {
            val repository = SettingsRepositoryImpl(SettingsDataStore(InMemoryDataStore()))
            val syncedAt = Instant.ofEpochMilli(1_755_000_000_000L)

            repository.setLastSyncedAt(syncedAt)

            assertEquals(syncedAt, repository.observeLastSyncedAt().first())
        }

    @Test
    fun theRepositoryReportsNeverSyncedAsNull() =
        runTest {
            val repository = SettingsRepositoryImpl(SettingsDataStore(InMemoryDataStore()))

            assertNull(repository.observeLastSyncedAt().first())
        }
}

/** A [DataStore] that keeps preferences in memory, with none of the file semantics. */
private class InMemoryDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}

/** A [DataStore] whose reads always fail, for the two paths a real file cannot easily produce. */
private class FailingDataStore(
    private val cause: Throwable,
) : DataStore<Preferences> {
    override val data: Flow<Preferences> = flow { throw cause }

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        transform(emptyPreferences())
}
