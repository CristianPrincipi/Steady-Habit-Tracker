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
    private val weightId = NotionPropertyId("%3FfMg")
    private val sleepId = NotionPropertyId("%7BQ%5Ct")
    private val databaseId = NotionDatabaseId("8f2c41d97b5e40a3ac6e1d9f0b73e254")

    private val mapping =
        NotionPropertyMapping(
            dataSourceId = NotionDataSourceId("2f0b1c8e-0000-4000-8000-000000000000"),
            dayKeyPropertyId = dateId,
            habitPropertyIds = setOf(readId, journalId),
        )

    @Test
    fun theDatabaseIdIsLearnedAfterTheMappingWasSaved() =
        runTest {
            // The order the app actually does it in: the wizard saves a mapping it cannot fill this
            // field in on — it holds a data source id — and the first sync supplies it.
            val store = NotionMappingStore(InMemoryDataStore())
            store.save(mapping)

            store.rememberDatabaseId(databaseId)

            assertEquals(databaseId, store.read()?.databaseId)
        }

    @Test
    fun learningTheDatabaseIdChangesNothingElseAboutTheMapping() =
        runTest {
            val store = NotionMappingStore(InMemoryDataStore())
            store.save(mapping)

            store.rememberDatabaseId(databaseId)

            assertEquals(mapping.copy(databaseId = databaseId), store.read())
        }

    @Test
    fun aDatabaseIdIsNotRememberedWhenThereIsNoMappingToPutItOn() =
        runTest {
            // A disconnect landing between the schema fetch and this call. Writing anyway would
            // leave a lone id in a file the app treats as "not configured".
            val store = NotionMappingStore(InMemoryDataStore())

            store.rememberDatabaseId(databaseId)

            assertNull(store.read())
        }

    @Test
    fun aMappingSavedBeforeM75HasNoDatabaseIdAndStillReads() =
        runTest {
            // The blob every existing install holds. A field added as required would have sent the
            // user back through setup to recover a link.
            val stored = NotionMappingStore(storeHolding(OLD_BLOB)).read()

            assertEquals(NotionDataSourceId("ds-1"), stored?.dataSourceId)
            assertNull(stored?.databaseId)
        }

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

    // --- measures (M15.4) ---------------------------------------------------------------------

    @Test
    fun aMeasureAndItsUnitSurviveTheRoundTrip() =
        runTest {
            // The unit is the only thing in this file the user typed rather than picked, and it
            // exists nowhere else — losing it silently would relabel their Stats tiles.
            val store = NotionMappingStore(InMemoryDataStore())

            store.save(mapping.copy(measures = listOf(NotionMeasure(weightId, "kg"), NotionMeasure(sleepId, "h"))))

            assertEquals(
                listOf(NotionMeasure(weightId, "kg"), NotionMeasure(sleepId, "h")),
                store.read()?.measures,
            )
        }

    @Test
    fun aMappingSavedBeforeM154HasNoMeasuresAndStillReads() =
        runTest {
            // ⭐ The same guarantee `database_id` got at M7.5, and it matters more here: the user
            // is connected *right now*, and a required field would have shown them the setup
            // wizard on the next launch to collect two columns they never asked for.
            val stored = NotionMappingStore(storeHolding(OLD_BLOB)).read()

            assertEquals(NotionDataSourceId("ds-1"), stored?.dataSourceId)
            assertEquals(emptyList<NotionMeasure>(), stored?.measures)
        }

    @Test
    fun aMeasureStoredWithNoUnitReadsAsBlankRatherThanFailing() =
        runTest {
            // The unit was added with the field; a blob written by a build that stored only ids
            // must still read, for the reason above.
            val store =
                NotionMappingStore(
                    storeHolding(
                        """{"data_source_id":"d","day_key_property_id":"M%3BBw",""" +
                            """"habit_property_ids":["ZI%40W"],"measures":[{"property_id":"%3FfMg"}]}""",
                    ),
                )

            assertEquals(listOf(NotionMeasure(weightId, "")), store.read()?.measures)
        }

    @Test
    fun aBlobNamingTheSameMeasureTwiceMeansNotMapped() =
        runTest {
            // ⚠ Different units on purpose. `NotionPropertyMapping`'s `require` is the only thing
            // rejecting this — the type cannot, because a measure is not a `Set` member keyed on
            // its id — and the two-unit case is the one a `Set` would have let through.
            val store =
                NotionMappingStore(
                    storeHolding(
                        """{"data_source_id":"d","day_key_property_id":"M%3BBw",""" +
                            """"habit_property_ids":["ZI%40W"],"measures":""" +
                            """[{"property_id":"%3FfMg","unit":"kg"},{"property_id":"%3FfMg","unit":"lb"}]}""",
                    ),
                )

            assertNull(store.mapping.first())
        }

    @Test
    fun aBlobMakingOneColumnBothAHabitAndAMeasureMeansNotMapped() =
        runTest {
            val store =
                NotionMappingStore(
                    storeHolding(
                        """{"data_source_id":"d","day_key_property_id":"M%3BBw",""" +
                            """"habit_property_ids":["ZI%40W"],"measures":[{"property_id":"ZI%40W","unit":"kg"}]}""",
                    ),
                )

            assertNull(store.mapping.first())
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

    private companion object {
        /**
         * A mapping as builds before M7.5 wrote it — three keys, no `database_id`.
         *
         * Written out rather than produced by the code under test on purpose: a fixture built by
         * `encode` would change shape the moment `encode` did, which is exactly the regression this
         * is here to catch.
         */
        const val OLD_BLOB =
            """{"data_source_id":"ds-1","day_key_property_id":"M%3BBw","habit_property_ids":["ZI%40W"]}"""
    }
}
