package com.cristianprincipi.habits.data.sync

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cristianprincipi.habits.core.testing.FailingDataStore
import com.cristianprincipi.habits.core.testing.InMemoryDataStore
import com.cristianprincipi.habits.data.preferences.NotionPropertyId
import com.cristianprincipi.habits.data.preferences.SettingsDataStore
import com.cristianprincipi.habits.data.remote.notion.NotionError
import com.cristianprincipi.habits.data.remote.notion.mapper.NotionMappingProblem
import com.cristianprincipi.habits.domain.model.MappingIssue
import com.cristianprincipi.habits.domain.model.SyncFailure
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

/**
 * The thing that makes a background failure survivable.
 *
 * Sync runs under WorkManager, so the process that learned a sync failed is usually gone before
 * anyone opens the app. Everything below is really one question asked several ways: **is what the
 * last run learned still there afterwards, and is it still the right shape?**
 *
 * In-memory rather than on a file, for the reason `SettingsDataStoreTest` documents: DataStore
 * renames a temporary file over the target and `File.renameTo` on Windows will not overwrite.
 */
class LastSyncFailureStoreTest {
    private val failed = SyncOutcome.Failed(NotionError.MissingOrNotShared)

    @Test
    fun aStoreNothingHasWrittenToReportsNoFailure() =
        runTest {
            assertNull(LastSyncFailureStore(InMemoryDataStore()).read())
        }

    @Test
    fun everySimpleFailureComesBackAsItself() =
        runTest {
            // One assertion per stored kind, because the kind strings are a storage format: a typo
            // in one of them would orphan exactly that value and nothing else would notice.
            val cases =
                mapOf(
                    NotionError.NotConnected to SyncFailure.NotConnected,
                    NotionError.Unauthorized to SyncFailure.TokenRejected,
                    NotionError.Forbidden to SyncFailure.MissingCapability,
                    NotionError.MissingOrNotShared to SyncFailure.DatabaseNotShared,
                    NotionError.Server(status = 503) to SyncFailure.NotionUnavailable,
                    NotionError.Network(IOException()) to SyncFailure.Offline,
                    NotionError.Malformed(IllegalStateException()) to SyncFailure.AppOutOfDate,
                )

            cases.forEach { (error, expected) ->
                val store = LastSyncFailureStore(InMemoryDataStore())

                store.record(SyncOutcome.Failed(error))

                assertEquals(expected, store.read())
            }
        }

    @Test
    fun aBrokenMappingKeepsEveryIssueAndEveryFieldOfIt() =
        runTest {
            // The most likely failure this app will ever show, and the only one carrying detail.
            // If the round trip loses a name or a type, M4.13 renders a shrug instead of an
            // instruction — which is the "clear, actionable error" CLAUDE.md §3.4 asks for, lost.
            val store = LastSyncFailureStore(InMemoryDataStore())
            val problems =
                listOf(
                    NotionMappingProblem.HabitMissing(NotionPropertyId("ZI%40W")),
                    NotionMappingProblem.HabitRetyped(NotionPropertyId("Ye%3Aq"), "✒️ Journal", "number"),
                    NotionMappingProblem.DayKeyRetyped(NotionPropertyId("M%3BBw"), "Date", "rich_text"),
                )

            store.record(SyncOutcome.MappingBroken(problems))

            assertEquals(
                SyncFailure.MappingBroken(
                    listOf(
                        MappingIssue.HabitMissing("ZI%40W"),
                        MappingIssue.HabitRetyped("Ye%3Aq", "✒️ Journal", "number"),
                        MappingIssue.DayKeyRetyped("M%3BBw", "Date", "rich_text"),
                    ),
                ),
                store.read(),
            )
        }

    @Test
    fun aRunThatWorkedClearsTheFailureBeforeIt() =
        runTest {
            // The reason `record` takes the whole outcome: there is no path that writes a failure
            // and forgets to clear one, because there is only one call.
            val store = LastSyncFailureStore(InMemoryDataStore())
            store.record(failed)

            store.record(SyncOutcome.Success(daysPulled = 2, daysPushed = 1))

            assertNull(store.read())
        }

    @Test
    fun anAppThatIsNotConnectedYetClearsItToo() =
        runTest {
            // NotConfigured is not a failure — a disconnect must not leave the last error standing.
            val store = LastSyncFailureStore(InMemoryDataStore())
            store.record(failed)

            store.record(SyncOutcome.NotConfigured)

            assertNull(store.read())
        }

    @Test
    fun theLatestRunIsTheOnlyOneKept() =
        runTest {
            // One value, overwritten. A history of failures is a feature nobody asked for.
            val store = LastSyncFailureStore(InMemoryDataStore())

            store.record(SyncOutcome.Failed(NotionError.Unauthorized))
            store.record(SyncOutcome.Failed(NotionError.Network(IOException())))

            assertEquals(SyncFailure.Offline, store.read())
        }

    @Test
    fun disconnectingForgetsTheError() =
        runTest {
            val store = LastSyncFailureStore(InMemoryDataStore())
            store.record(failed)

            store.clear()

            assertNull(store.read())
        }

    @Test
    fun clearingTheErrorLeavesTheLastSyncedTimeAlone() =
        runTest {
            // Both live in the settings file. A user who disconnects has not asked to forget when
            // their data was last correct — the same rule NotionMappingStore.clear() follows.
            val dataStore = InMemoryDataStore()
            SettingsDataStore(dataStore).setLastSyncedAtEpochMillis(1_755_000_000_000L)
            val store = LastSyncFailureStore(dataStore)
            store.record(failed)

            store.clear()

            assertEquals(1_755_000_000_000L, SettingsDataStore(dataStore).lastSyncedAtEpochMillis.first())
        }

    @Test
    fun aBlobThisBuildCannotReadReportsNoFailure() =
        runTest {
            // Deliberate flattening: this value drives a banner, and the next run rewrites it. A
            // crash on a corrupt file would cost the screen entirely.
            val dataStore = InMemoryDataStore()
            dataStore.edit { it[FAILURE] = "{" }

            assertNull(LastSyncFailureStore(dataStore).read())
        }

    @Test
    fun aFailureKindFromALaterBuildReportsNoFailure() =
        runTest {
            val dataStore = InMemoryDataStore()
            dataStore.edit { it[FAILURE] = """{"kind":"a_way_to_fail_invented_later"}""" }

            assertNull(LastSyncFailureStore(dataStore).read())
        }

    @Test
    fun oneUnreadableIssueDoesNotHideTheOthers() =
        runTest {
            // The rule the property codec set (ADR-044): drop what cannot be read, keep what can.
            val dataStore = InMemoryDataStore()
            dataStore.edit {
                it[FAILURE] =
                    """
                    {"kind":"mapping_broken","issues":[
                      {"kind":"something_new"},
                      {"kind":"habit_missing","property_id":"ZI%40W"}
                    ]}
                    """.trimIndent()
            }

            assertEquals(
                SyncFailure.MappingBroken(listOf(MappingIssue.HabitMissing("ZI%40W"))),
                LastSyncFailureStore(dataStore).read(),
            )
        }

    @Test
    fun aRetypeMissingItsNameIsNotHalfAnIssue() =
        runTest {
            // All three fields or nothing: "Journal is no longer a" is not a sentence.
            val dataStore = InMemoryDataStore()
            dataStore.edit {
                it[FAILURE] = """{"kind":"mapping_broken","issues":[{"kind":"habit_retyped","property_id":"a"}]}"""
            }

            assertNull(LastSyncFailureStore(dataStore).read())
        }

    @Test
    fun aBrokenMappingWithNothingLeftToSayIsNoFailureAtAll() =
        runTest {
            // MappingBroken.issues is documented as never empty, and an alert naming no problem is
            // worse than the next run's alert a quarter of an hour later.
            val dataStore = InMemoryDataStore()
            dataStore.edit { it[FAILURE] = """{"kind":"mapping_broken","issues":[{"kind":"something_new"}]}""" }

            assertNull(LastSyncFailureStore(dataStore).read())
        }

    @Test
    fun anUnreadableFileReportsNoFailureRatherThanThrowing() =
        runTest {
            // DataStore signals a missing or unreadable file as an IOException on the flow. The
            // collector behind this is a status banner; taking it down would be the worse trade.
            val store = LastSyncFailureStore(FailingDataStore(IOException("no such file")))

            assertNull(store.read())
        }

    private companion object {
        /** The production key, spelled out here so a rename to it fails this test loudly. */
        val FAILURE = stringPreferencesKey("sync_last_failure")
    }
}
