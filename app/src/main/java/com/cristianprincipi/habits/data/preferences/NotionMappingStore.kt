package com.cristianprincipi.habits.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the user's [NotionPropertyMapping] is kept.
 *
 * **In the settings file, not the token's.** `AuthDataStoreModule` keeps the token in a file of its
 * own so that disconnect can wipe it wholesale, and its KDoc is explicit that the file holds
 * ciphertext only. The mapping is not a secret — it is a handful of opaque ids naming columns in a
 * database the app cannot reach without the token — and putting plaintext configuration in there
 * would cost that file the one property that makes it easy to reason about.
 *
 * **Stored as one JSON string under one key, rather than three keys.** A data source id without a
 * day key is not half a mapping, it is a broken one, and `DataStore.edit` gives one blob
 * atomicity for free. It also keeps the habit ids **ordered**, which a `stringSetPreferencesKey`
 * would not — `HabitEntity.sortOrder` comes from that order — and it is safe for ids containing any
 * character, which a delimited string would not be: property ids are percent-encoded and this app
 * never inspects their contents.
 *
 * Sharing the file with [SettingsDataStore] is deliberate and safe: DataStore allows one instance
 * per file (ADR-032) and Hilt provides exactly one, so both classes write through the same actor.
 */
@Singleton
class NotionMappingStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        /**
         * The saved mapping, or `null` when the app has never been connected.
         *
         * **`null` is also what an unreadable or undecodable blob reports**, and that is a
         * deliberate flattening. `CLAUDE.md` §6 requires that a value this build cannot parse never
         * break a sync, and the recovery here is cheap in a way most of this app's data is not: a
         * mapping is a few taps to rebuild, whereas the history it points at is not. So a mapping
         * we cannot read means "not mapped", the setup screen appears, and nothing is destroyed.
         *
         * The [IOException] catch is the same contract [SettingsDataStore] documents — DataStore
         * signals a missing or unreadable file on the flow, and anything that is not an
         * [IOException] is a real bug and still propagates.
         */
        val mapping: Flow<NotionPropertyMapping?> =
            dataStore.data
                .catch { cause ->
                    if (cause is IOException) emit(emptyPreferences()) else throw cause
                }.map { preferences -> preferences[MAPPING]?.let(::decode) }

        /** The mapping right now. What the sync engine reads before it validates against the schema. */
        suspend fun read(): NotionPropertyMapping? = mapping.first()

        suspend fun save(mapping: NotionPropertyMapping) {
            dataStore.edit { preferences -> preferences[MAPPING] = encode(mapping) }
        }

        /**
         * Records where the database lives, if that is news.
         *
         * ⭐ **A read-modify-write inside one `edit`, not a `read()` then a `save()`.** DataStore
         * runs the block under its own lock, so the mapping this reads is the one it writes back.
         * The two-call version would race the user re-mapping in another screen and could put a
         * whole stale mapping back on top of a fresh one — to record a field neither of them typed.
         *
         * **Silent when there is nothing to do**: no mapping stored (a disconnect landed first) or
         * the same id already held. The common case is every sync after the first, and it must not
         * cost a file write.
         */
        suspend fun rememberDatabaseId(databaseId: NotionDatabaseId) {
            dataStore.edit { preferences ->
                val current = preferences[MAPPING]?.let(::decode) ?: return@edit
                if (current.databaseId == databaseId) return@edit
                preferences[MAPPING] = encode(current.copy(databaseId = databaseId))
            }
        }

        /**
         * Forgets the connection's shape. Part of disconnect (`CLAUDE.md` §3.6), beside the token.
         *
         * Removes one key rather than clearing the file: `lastSyncedAt` lives here too, and the user
         * disconnecting has not asked to forget when their data was last correct.
         */
        suspend fun clear() {
            dataStore.edit { preferences -> preferences.remove(MAPPING) }
        }

        private fun encode(mapping: NotionPropertyMapping): String =
            json.encodeToString(
                StoredMapping(
                    dataSourceId = mapping.dataSourceId.value,
                    dayKeyPropertyId = mapping.dayKeyPropertyId.value,
                    habitPropertyIds = mapping.habitPropertyIds.map(NotionPropertyId::value),
                    databaseId = mapping.databaseId?.value,
                ),
            )

        /**
         * @return the mapping, or `null` for anything this build cannot turn into one.
         *
         * **A repeated habit id is rejected, not deduplicated.** [NotionPropertyMapping] would
         * happily collapse it — a `Set` cannot hold it twice — and that would be the right answer
         * if the duplicate came from a user picking a property twice. It cannot: this app is the
         * only writer of this file, and it writes a set. A repeat therefore means the blob was not
         * written by this app, and silently syncing "what we could make of it" is exactly the
         * quiet data loss ADR-035 is about. Better to show the setup screen.
         */
        private fun decode(stored: String): NotionPropertyMapping? =
            runCatching {
                val storedMapping = json.decodeFromString<StoredMapping>(stored)
                require(storedMapping.habitPropertyIds.distinct().size == storedMapping.habitPropertyIds.size) {
                    "The stored mapping names the same habit property twice."
                }
                NotionPropertyMapping(
                    dataSourceId = NotionDataSourceId(storedMapping.dataSourceId),
                    dayKeyPropertyId = NotionPropertyId(storedMapping.dayKeyPropertyId),
                    // List -> LinkedHashSet: the stored order is the habit order.
                    habitPropertyIds = storedMapping.habitPropertyIds.map(::NotionPropertyId).toSet(),
                    databaseId = storedMapping.databaseId?.let(::NotionDatabaseId),
                )
            }.getOrNull()

        /**
         * The on-disk shape, kept separate from [NotionPropertyMapping] for the same reason a Room
         * entity is kept separate from a domain model: renaming a field in the model is a refactor,
         * and renaming a field here silently orphans every user's saved mapping. The names below are
         * a storage format, so they are spelled out rather than inherited from Kotlin identifiers.
         */
        @Serializable
        private data class StoredMapping(
            @SerialName("data_source_id") val dataSourceId: String,
            @SerialName("day_key_property_id") val dayKeyPropertyId: String,
            @SerialName("habit_property_ids") val habitPropertyIds: List<String>,
            /**
             * Absent from every mapping written before M7.5, hence the default.
             *
             * A missing database id is not a broken mapping — it is one saved by a build that did
             * not know to ask, and the next sync fills it in. Making it required would send every
             * existing user back through setup for a field that only draws a link.
             */
            @SerialName("database_id") val databaseId: String? = null,
        )

        private companion object {
            val MAPPING = stringPreferencesKey("notion_property_mapping")

            /**
             * `ignoreUnknownKeys` for our own file, which sounds odd until the downgrade case: a
             * later version adding a field must not make this one refuse to read the mapping and
             * send the user back through setup.
             */
            val json = Json { ignoreUnknownKeys = true }
        }
    }
