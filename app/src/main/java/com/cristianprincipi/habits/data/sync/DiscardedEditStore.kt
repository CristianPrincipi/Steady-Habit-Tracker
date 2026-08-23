package com.cristianprincipi.habits.data.sync

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

/** Which copy of a day was thrown away when last-write-wins decided. */
enum class SyncSide {
    LOCAL,
    REMOTE,
}

/**
 * A record that resolution destroyed something, kept so the app can admit to it.
 *
 * `CLAUDE.md` §3.5: "Where resolution discards a user's edit, say so in the UI. Never resolve
 * destructively and silently." Both halves of that sentence need this type to exist. Sync runs in
 * the background under WorkManager (M4.10), so a discard that lived only in the run's return value
 * would be gone before any screen could show it — which is the definition of silently.
 *
 * **In a single-user app both sides are the same person**, which is why [discardedSide] matters
 * rather than being an implementation detail: "the change you made on your phone was replaced by
 * your later change in Notion" and its opposite are different sentences, and the user is entitled
 * to know which one happened.
 *
 * @param habitIds local habit ids, not names. Names change upstream (ADR-038) and a notice written
 *   last week should render with the name the habit has today, so M4.13 joins these against the
 *   habits table rather than reading a name frozen in here.
 */
data class DiscardedEdit(
    val epochDay: Long,
    val habitIds: Set<String>,
    val discardedSide: SyncSide,
    val resolvedAtEpochMillis: Long,
)

/**
 * Where [DiscardedEdit]s wait for someone to read them.
 *
 * **Not a Room table.** These are notices, not history: they are about the app's behaviour rather
 * than about the user's habits, they are dismissible, and nothing joins against them. A table would
 * buy queryability nothing needs and cost a schema migration (`CLAUDE.md` §6). They live in the
 * settings DataStore beside the property mapping, in the same one-JSON-blob shape and for the same
 * reasons — see `NotionMappingStore`.
 *
 * **Bounded at [LIMIT], newest first.** An unbounded log of a rare event is still unbounded, and
 * this file is read whole on every emission. Losing the oldest notice when fifty have piled up is
 * not a loss worth a migration: by then the user has either read them or stopped caring.
 */
@Singleton
class DiscardedEditStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        /**
         * Every notice not yet dismissed, newest first.
         *
         * An unreadable or undecodable blob reads as *no notices*, the same flattening
         * `NotionMappingStore` documents. It is the safe direction here too: the alternative is a
         * screen that crashes on a corrupt notices file, which would be a worse outcome than a
         * notice the user never sees.
         */
        val edits: Flow<List<DiscardedEdit>> =
            dataStore.data
                .catch { cause ->
                    if (cause is IOException) emit(emptyPreferences()) else throw cause
                }.map { preferences -> preferences[EDITS]?.let(::decode).orEmpty() }

        /**
         * Adds what one run discarded, newest first.
         *
         * Appending rather than replacing, because two background runs can both discard something
         * before the user opens the app once.
         */
        suspend fun record(discarded: List<DiscardedEdit>) {
            if (discarded.isEmpty()) return
            dataStore.edit { preferences ->
                val existing = preferences[EDITS]?.let(::decode).orEmpty()
                preferences[EDITS] = encode((discarded + existing).take(LIMIT))
            }
        }

        /** The user has seen them. Also part of disconnect, beside the token and the mapping. */
        suspend fun clear() {
            dataStore.edit { preferences -> preferences.remove(EDITS) }
        }

        /** What is waiting right now. */
        suspend fun read(): List<DiscardedEdit> = edits.first()

        private fun encode(edits: List<DiscardedEdit>): String =
            json.encodeToString(
                edits.map { edit ->
                    StoredEdit(
                        epochDay = edit.epochDay,
                        habitIds = edit.habitIds.toList(),
                        discardedSide = edit.discardedSide.name,
                        resolvedAtEpochMillis = edit.resolvedAtEpochMillis,
                    )
                },
            )

        /**
         * @return the notices, or `null` for a blob this build cannot read.
         *
         * A side this build does not know is dropped rather than failing the whole list — one
         * unreadable notice must not hide the others. That is the same rule the property codec
         * follows (ADR-044), applied to our own file.
         */
        private fun decode(stored: String): List<DiscardedEdit>? =
            runCatching {
                json.decodeFromString<List<StoredEdit>>(stored).mapNotNull { edit ->
                    val side = SyncSide.entries.firstOrNull { it.name == edit.discardedSide }
                    side?.let {
                        DiscardedEdit(
                            epochDay = edit.epochDay,
                            habitIds = edit.habitIds.toSet(),
                            discardedSide = it,
                            resolvedAtEpochMillis = edit.resolvedAtEpochMillis,
                        )
                    }
                }
            }.getOrNull()

        /** The on-disk shape, named separately from the model for the reason `NotionMappingStore` gives. */
        @Serializable
        private data class StoredEdit(
            @SerialName("epoch_day") val epochDay: Long,
            @SerialName("habit_ids") val habitIds: List<String>,
            @SerialName("discarded_side") val discardedSide: String,
            @SerialName("resolved_at_epoch_millis") val resolvedAtEpochMillis: Long,
        )

        private companion object {
            val EDITS = stringPreferencesKey("sync_discarded_edits")
            const val LIMIT = 50
            val json = Json { ignoreUnknownKeys = true }
        }
    }
