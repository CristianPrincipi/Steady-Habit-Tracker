package com.cristianprincipi.habits.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cristianprincipi.habits.core.testing.FailingDataStore
import com.cristianprincipi.habits.core.testing.InMemoryDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

/**
 * The mapping store, over an in-memory [DataStore] for the reason `SettingsDataStoreTest` documents:
 * `File.renameTo` on Windows will not overwrite, so a real-file DataStore on this machine accepts
 * exactly one write. The real file is proved on device.
 *
 * The ids in these fixtures are percent-encoded, as Notion's are. That is not decoration: a store
 * that helpfully decoded, trimmed or split on a delimiter would pass every test written with tidy
 * ids and then fail against the user's actual database (`docs/notion-schema.md` §6.1).
 */
class NotionMappingStoreTest {
    private val readId = NotionPropertyId("ZI%40W")
    private val journalId = NotionPropertyId("Ye%3Aq")
    private val dateId = NotionPropertyId("M%3BBw")

    private val mapping =
        NotionPropertyMapping(
            dataSourceId = NotionDataSourceId("2f0b1c8e-0000-4000-8000-000000000000"),
            dayKeyPropertyId = dateId,
            habitPropertyIds = setOf(readId, journalId),
        )

    @Test
    fun aFirstRunHasNoMapping() =
        runTest {
            assertNull(NotionMappingStore(InMemoryDataStore()).mapping.first())
        }

    @Test
    fun aSavedMappingComesBackWithItsIdsUntouched() =
        runTest {
            val store = NotionMappingStore(InMemoryDataStore())

            store.save(mapping)

            assertEquals(mapping, store.read())
        }

    @Test
    fun theHabitOrderSurvivesTheRoundTrip() =
        runTest {
            // A stringSetPreferencesKey would lose this, and `sortOrder` follows it.
            val store = NotionMappingStore(InMemoryDataStore())

            store.save(mapping)

            assertEquals(listOf(readId, journalId), store.read()?.habitPropertyIds?.toList())
        }

    @Test
    fun remappingReplacesTheMappingBeforeIt() =
        runTest {
            val store = NotionMappingStore(InMemoryDataStore())
            val fewer = mapping.copy(habitPropertyIds = setOf(journalId))

            store.save(mapping)
            store.save(fewer)

            assertEquals(fewer, store.read())
        }

    @Test
    fun disconnectingForgetsTheMappingAndLeavesTheLastSyncTimeAlone() =
        runTest {
            // Both live in the settings file, so `clear` removes a key rather than wiping the file.
            // Disconnecting says nothing about wanting to forget when the data was last correct.
            val dataStore = InMemoryDataStore()
            val store = NotionMappingStore(dataStore)
            val settings = SettingsDataStore(dataStore)
            settings.setLastSyncedAtEpochMillis(1_755_000_000_000L)
            store.save(mapping)

            store.clear()

            assertNull(store.read())
            assertEquals(1_755_000_000_000L, settings.lastSyncedAtEpochMillis.first())
        }

    @Test
    fun aBlobThatIsNotJsonMeansNotMapped() =
        runTest {
            // Rather than throwing out of a flow the UI collects. A mapping is a few taps to
            // rebuild; the history it points at is not, and nothing here touches that.
            val store = NotionMappingStore(storeHolding("{ not json"))

            assertNull(store.mapping.first())
        }

    @Test
    fun aBlobMissingTheDayKeyMeansNotMapped() =
        runTest {
            val store =
                NotionMappingStore(
                    storeHolding("""{"data_source_id":"d","habit_property_ids":["ZI%40W"]}"""),
                )

            assertNull(store.mapping.first())
        }

    @Test
    fun aBlobNamingTheSameHabitTwiceIsRejectedRatherThanDeduplicated() =
        runTest {
            // This app is the only writer of this file and it writes a set, so a repeat means the
            // blob did not come from here. Quietly syncing "what we could make of it" is the
            // silent drop ADR-035 is about.
            val store =
                NotionMappingStore(
                    storeHolding(
                        """{"data_source_id":"d","day_key_property_id":"M%3BBw",""" +
                            """"habit_property_ids":["ZI%40W","ZI%40W"]}""",
                    ),
                )

            assertNull(store.mapping.first())
        }

    @Test
    fun aBlobWhoseDayKeyIsAlsoAHabitIsRejected() =
        runTest {
            // The model's own invariant, reached through the store: `require` throws inside decode
            // and comes back as "not mapped" rather than as a crash on a flow.
            val store =
                NotionMappingStore(
                    storeHolding(
                        """{"data_source_id":"d","day_key_property_id":"M%3BBw",""" +
                            """"habit_property_ids":["M%3BBw"]}""",
                    ),
                )

            assertNull(store.mapping.first())
        }

    @Test
    fun aFieldFromALaterVersionDoesNotDiscardTheMapping() =
        runTest {
            // ignoreUnknownKeys applies to our own file too: a downgrade must not send the user
            // back through setup.
            val store =
                NotionMappingStore(
                    storeHolding(
                        """{"data_source_id":"d","day_key_property_id":"M%3BBw",""" +
                            """"habit_property_ids":["ZI%40W"],"weight_property_id":"x"}""",
                    ),
                )

            assertEquals(setOf(readId), store.mapping.first()?.habitPropertyIds)
        }

    @Test
    fun anUnreadableFileMeansNotMappedInsteadOfThrowing() =
        runTest {
            val store = NotionMappingStore(FailingDataStore(IOException("corrupt")))

            assertNull(store.mapping.first())
        }

    @Test
    fun aFailureThatIsNotIoStillPropagates() =
        runTest {
            val store = NotionMappingStore(FailingDataStore(IllegalStateException("a real bug")))

            val thrown = runCatching { store.mapping.first() }.exceptionOrNull()

            assertEquals("a real bug", thrown?.message)
        }

    /**
     * A store already holding [blob] under the mapping's key.
     *
     * The key is spelled out here on purpose — it is a storage name that outlives this code, and a
     * rename that goes unnoticed would silently orphan every user's saved mapping.
     */
    private suspend fun storeHolding(blob: String): DataStore<Preferences> =
        InMemoryDataStore().apply {
            edit { preferences -> preferences[stringPreferencesKey("notion_property_mapping")] = blob }
        }
}
