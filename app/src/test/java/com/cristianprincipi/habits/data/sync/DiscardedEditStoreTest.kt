package com.cristianprincipi.habits.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cristianprincipi.habits.core.testing.FailingDataStore
import com.cristianprincipi.habits.core.testing.InMemoryDataStore
import com.cristianprincipi.habits.data.preferences.SettingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The notices that make `CLAUDE.md` §3.5's "never resolve destructively and silently" true.
 *
 * In-memory rather than on a file, for the reason `SettingsDataStoreTest` documents: DataStore
 * renames a temporary file over the target and `File.renameTo` on Windows will not overwrite.
 */
class DiscardedEditStoreTest {
    private val read = "habit-read"
    private val journal = "habit-journal"

    private fun edit(
        epochDay: Long,
        side: SyncSide = SyncSide.LOCAL,
        habitIds: Set<String> = setOf(read),
    ) = DiscardedEdit(
        epochDay = epochDay,
        habitIds = habitIds,
        discardedSide = side,
        resolvedAtEpochMillis = 1_755_000_000_000L,
    )

    @Test
    fun aRunThatDestroyedNothingLeavesNoNotice() =
        runTest {
            // The overwhelmingly common case, and it must not write to the file at all.
            val store = DiscardedEditStore(InMemoryDataStore())

            store.record(emptyList())

            assertEquals(emptyList<DiscardedEdit>(), store.read())
        }

    @Test
    fun aNoticeComesBackWithEveryFieldItWentInWith() =
        runTest {
            val store = DiscardedEditStore(InMemoryDataStore())
            val discarded = edit(20_000, SyncSide.REMOTE, setOf(read, journal))

            store.record(listOf(discarded))

            assertEquals(listOf(discarded), store.read())
        }

    @Test
    fun laterRunsAppendRatherThanReplace() =
        runTest {
            // Two background runs can both discard something before the user opens the app once.
            val store = DiscardedEditStore(InMemoryDataStore())

            store.record(listOf(edit(20_000)))
            store.record(listOf(edit(20_001)))

            assertEquals(listOf(20_001L, 20_000L), store.read().map(DiscardedEdit::epochDay))
        }

    @Test
    fun theNewestNoticesAreTheOnesKept() =
        runTest {
            // Bounded at 50: an unbounded log of a rare event is still unbounded, and this file is
            // read whole on every emission.
            val store = DiscardedEditStore(InMemoryDataStore())

            repeat(60) { index -> store.record(listOf(edit(20_000L + index))) }

            val kept = store.read()
            assertEquals(50, kept.size)
            assertEquals(20_059L, kept.first().epochDay)
            assertEquals(20_010L, kept.last().epochDay)
        }

    @Test
    fun dismissingThemLeavesNothingBehind() =
        runTest {
            val store = DiscardedEditStore(InMemoryDataStore())
            store.record(listOf(edit(20_000)))

            store.clear()

            assertEquals(emptyList<DiscardedEdit>(), store.read())
        }

    @Test
    fun dismissingThemDoesNotDisturbTheRestOfTheSettingsFile() =
        runTest {
            // They share a file with `lastSyncedAt` and the property mapping.
            val dataStore = InMemoryDataStore()
            val store = DiscardedEditStore(dataStore)
            val settings = SettingsDataStore(dataStore)
            settings.setLastSyncedAtEpochMillis(1_755_000_000_000L)
            store.record(listOf(edit(20_000)))

            store.clear()

            assertEquals(1_755_000_000_000L, settings.lastSyncedAtEpochMillis.first())
        }

    @Test
    fun aBlobThatIsNotJsonReadsAsNoNotices() =
        runTest {
            // Rather than throwing out of a flow a screen collects. A lost notice is bad; a sync
            // status screen that crashes on one is worse.
            val store = DiscardedEditStore(storeHolding("{ not json"))

            assertEquals(emptyList<DiscardedEdit>(), store.edits.first())
        }

    @Test
    fun aNoticeNamingASideThisBuildDoesNotKnowIsDroppedWithoutHidingTheOthers() =
        runTest {
            // ADR-044's rule — one unreadable value must not take the readable ones with it —
            // applied to our own file.
            val store =
                DiscardedEditStore(
                    storeHolding(
                        """[{"epoch_day":1,"habit_ids":["a"],"discarded_side":"SIDEWAYS",""" +
                            """"resolved_at_epoch_millis":1},{"epoch_day":2,"habit_ids":["b"],""" +
                            """"discarded_side":"LOCAL","resolved_at_epoch_millis":2}]""",
                    ),
                )

            assertEquals(listOf(2L), store.edits.first().map(DiscardedEdit::epochDay))
        }

    @Test
    fun anUnreadableFileReadsAsNoNoticesInsteadOfThrowing() =
        runTest {
            val store = DiscardedEditStore(FailingDataStore(IOException("corrupt")))

            assertTrue(store.edits.first().isEmpty())
        }

    @Test
    fun aFailureThatIsNotIoStillPropagates() =
        runTest {
            val store = DiscardedEditStore(FailingDataStore(IllegalStateException("a real bug")))

            val thrown = runCatching { store.edits.first() }.exceptionOrNull()

            assertEquals("a real bug", thrown?.message)
        }

    /** A store already holding [blob] under the notices' key, which is spelled out as a storage name. */
    private suspend fun storeHolding(blob: String): DataStore<Preferences> =
        InMemoryDataStore().apply {
            edit { preferences -> preferences[stringPreferencesKey("sync_discarded_edits")] = blob }
        }
}
