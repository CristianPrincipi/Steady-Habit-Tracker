package com.cristianprincipi.habits.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cristianprincipi.habits.data.remote.notion.NotionError
import com.cristianprincipi.habits.data.remote.notion.mapper.NotionMappingProblem
import com.cristianprincipi.habits.domain.model.MappingIssue
import com.cristianprincipi.habits.domain.model.SyncFailure
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
 * How the last run got on, kept so that a background failure survives to be rendered.
 *
 * **This exists because sync runs under WorkManager.** [SyncEngine.sync] answers with a
 * [SyncOutcome] and `SyncWorker` turns it into a `Result` — and until this step, dropped it. A
 * failure that lived only in that return value was gone before any screen could show it, which is
 * the problem [DiscardedEditStore] solved in M4.9 and the same solution: one JSON blob in the
 * settings DataStore, beside the property mapping. `CLAUDE.md` §3.5 requires a failed sync to be "a
 * clear, non-blocking, human-readable message with a retry affordance"; a message nobody was awake
 * to hear is none of those things.
 *
 * **What is stored is already the domain [SyncFailure], not the wire error.** Translating at write
 * time rather than read time means the on-disk shape follows what the UI needs rather than what
 * Notion's HTTP codes happen to be, so a change to [NotionError] cannot orphan a stored value. It
 * is also the boundary `CLAUDE.md` §6 asks for, kept in the layer allowed to see both sides.
 *
 * **One value, overwritten, not a log.** The question this answers is "did the last run work?" — a
 * history of failures is a different feature nobody has asked for, and [DiscardedEditStore] is
 * already the place for things that accumulate.
 */
@Singleton
class LastSyncFailureStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        /**
         * Why the last run did not sync, or `null` if it did — or if none has ever run.
         *
         * **A blob this build cannot read also reports `null`**, the same flattening
         * `NotionMappingStore` documents and for a stronger reason here: this value only ever drives
         * a banner. Reporting "no failure" for a moment costs one uninformative screen, and the next
         * run — fifteen minutes away at worst — writes a value this build can read. A crash on a
         * corrupt file would cost the screen entirely.
         *
         * The [IOException] catch is the contract
         * [com.cristianprincipi.habits.data.preferences.SettingsDataStore] documents: DataStore
         * signals a missing or unreadable file on the flow, and anything that is not an
         * [IOException] is a real bug and still propagates.
         */
        val failure: Flow<SyncFailure?> =
            dataStore.data
                .catch { cause ->
                    if (cause is IOException) emit(emptyPreferences()) else throw cause
                }.map { preferences -> preferences[FAILURE]?.let(::decode) }

        /**
         * Records what one run produced — **including a run that worked**, which clears whatever the
         * last failure was.
         *
         * Taking the whole [SyncOutcome] rather than a nullable failure is what makes that true by
         * construction: there is no way to write a failure and forget to clear one, because there is
         * one call and every path through `SyncWorker` makes it.
         */
        suspend fun record(outcome: SyncOutcome) {
            val failure = outcome.failure
            dataStore.edit { preferences ->
                if (failure == null) {
                    preferences.remove(FAILURE)
                } else {
                    preferences[FAILURE] = encode(failure)
                }
            }
        }

        /** What is stored right now. */
        suspend fun read(): SyncFailure? = failure.first()

        /**
         * Forgets the last failure. Part of disconnect, beside the token, the mapping and the
         * discarded-edit notices — an error about a database the app is no longer connected to is
         * not something to keep showing.
         *
         * Removes one key rather than clearing the file: `lastSyncedAt` lives here too.
         */
        suspend fun clear() {
            dataStore.edit { preferences -> preferences.remove(FAILURE) }
        }

        private fun encode(failure: SyncFailure): String = json.encodeToString(failure.stored())

        /**
         * @return the failure, or `null` for anything this build cannot turn into one.
         *
         * A [SyncFailure.MappingBroken] whose issues all turned out to be unreadable comes back
         * `null` rather than as a broken mapping with nothing to say about it —
         * [SyncFailure.MappingBroken.issues] is documented as never empty, and an alert that names
         * no problem is worse than the next run's alert a quarter of an hour later.
         */
        private fun decode(stored: String): SyncFailure? =
            runCatching {
                val storedFailure = json.decodeFromString<StoredFailure>(stored)
                when (storedFailure.kind) {
                    NOT_CONNECTED -> SyncFailure.NotConnected
                    TOKEN_REJECTED -> SyncFailure.TokenRejected
                    MISSING_CAPABILITY -> SyncFailure.MissingCapability
                    DATABASE_NOT_SHARED -> SyncFailure.DatabaseNotShared
                    OFFLINE -> SyncFailure.Offline
                    NOTION_UNAVAILABLE -> SyncFailure.NotionUnavailable
                    APP_OUT_OF_DATE -> SyncFailure.AppOutOfDate
                    MAPPING_BROKEN -> mappingBroken(storedFailure.issues)
                    else -> null
                }
            }.getOrNull()

        private fun mappingBroken(issues: List<StoredIssue>): SyncFailure? =
            issues
                .mapNotNull { it.toIssue() }
                .takeIf(List<MappingIssue>::isNotEmpty)
                ?.let(SyncFailure::MappingBroken)

        private fun SyncFailure.stored(): StoredFailure =
            when (this) {
                SyncFailure.NotConnected -> StoredFailure(NOT_CONNECTED)
                SyncFailure.TokenRejected -> StoredFailure(TOKEN_REJECTED)
                SyncFailure.MissingCapability -> StoredFailure(MISSING_CAPABILITY)
                SyncFailure.DatabaseNotShared -> StoredFailure(DATABASE_NOT_SHARED)
                SyncFailure.Offline -> StoredFailure(OFFLINE)
                SyncFailure.NotionUnavailable -> StoredFailure(NOTION_UNAVAILABLE)
                SyncFailure.AppOutOfDate -> StoredFailure(APP_OUT_OF_DATE)
                is SyncFailure.MappingBroken -> StoredFailure(MAPPING_BROKEN, issues.map { it.stored() })
            }

        /**
         * The on-disk shape, kept separate from [SyncFailure] for the reason `NotionMappingStore`
         * gives: renaming a field in the model is a refactor, and renaming one here silently orphans
         * every stored value. The names below are a storage format, spelled out rather than
         * inherited from Kotlin identifiers.
         */
        @Serializable
        private data class StoredFailure(
            val kind: String,
            val issues: List<StoredIssue> = emptyList(),
        )

        @Serializable
        private data class StoredIssue(
            val kind: String,
            @SerialName("property_id") val propertyId: String? = null,
            val name: String? = null,
            @SerialName("actual_type") val actualType: String? = null,
        )

        private companion object {
            val FAILURE = stringPreferencesKey("sync_last_failure")
            val json = Json { ignoreUnknownKeys = true }

            const val NOT_CONNECTED = "not_connected"
            const val TOKEN_REJECTED = "token_rejected"
            const val MISSING_CAPABILITY = "missing_capability"
            const val DATABASE_NOT_SHARED = "database_not_shared"
            const val MAPPING_BROKEN = "mapping_broken"
            const val OFFLINE = "offline"
            const val NOTION_UNAVAILABLE = "notion_unavailable"
            const val APP_OUT_OF_DATE = "app_out_of_date"

            const val DAY_KEY_MISSING = "day_key_missing"
            const val DAY_KEY_RETYPED = "day_key_retyped"
            const val HABIT_MISSING = "habit_missing"
            const val HABIT_RETYPED = "habit_retyped"

            // M15.4. ⚠ These two are a **storage format**, like `StoredMapping`'s field names:
            // changing a string here orphans a notice already written to a user's device.
            const val MEASURE_MISSING = "measure_missing"
            const val MEASURE_RETYPED = "measure_retyped"
            const val WRONG_DATA_SOURCE = "wrong_data_source"

            /**
             * An issue this build cannot read is dropped rather than failing the whole list — one
             * unreadable problem must not hide the others. The rule the property codec follows
             * (ADR-044), and [DiscardedEditStore] after it, applied to our own file.
             */
            fun StoredIssue.toIssue(): MappingIssue? =
                when (kind) {
                    DAY_KEY_MISSING -> propertyId?.let(MappingIssue::DayKeyMissing)

                    HABIT_MISSING -> propertyId?.let(MappingIssue::HabitMissing)

                    WRONG_DATA_SOURCE -> MappingIssue.WrongDataSource

                    DAY_KEY_RETYPED -> retyped(MappingIssue::DayKeyRetyped)

                    HABIT_RETYPED -> retyped(MappingIssue::HabitRetyped)

                    MEASURE_MISSING -> propertyId?.let(MappingIssue::MeasureMissing)

                    MEASURE_RETYPED -> retyped(MappingIssue::MeasureRetyped)

                    // ⚠⚠ The `else` above is why these two lines had to be written by hand. The
                    // reverse `when` below is exhaustive and stopped the build; this one is keyed
                    // on a String, so a missing kind is not a compile error — it is a stored
                    // notice that reads back as nothing at all.
                    else -> null
                }

            /** A retype needs all three fields; any one missing makes the notice unreadable. */
            fun StoredIssue.retyped(build: (String, String, String) -> MappingIssue): MappingIssue? {
                if (propertyId == null || name == null || actualType == null) return null
                return build(propertyId, name, actualType)
            }

            fun MappingIssue.stored(): StoredIssue =
                when (this) {
                    is MappingIssue.DayKeyMissing -> {
                        StoredIssue(DAY_KEY_MISSING, propertyId)
                    }

                    is MappingIssue.HabitMissing -> {
                        StoredIssue(HABIT_MISSING, propertyId)
                    }

                    MappingIssue.WrongDataSource -> {
                        StoredIssue(WRONG_DATA_SOURCE)
                    }

                    is MappingIssue.DayKeyRetyped -> {
                        StoredIssue(DAY_KEY_RETYPED, propertyId, name, actualType)
                    }

                    is MappingIssue.HabitRetyped -> {
                        StoredIssue(HABIT_RETYPED, propertyId, name, actualType)
                    }

                    is MappingIssue.MeasureMissing -> {
                        StoredIssue(MEASURE_MISSING, propertyId)
                    }

                    is MappingIssue.MeasureRetyped -> {
                        StoredIssue(MEASURE_RETYPED, propertyId, name, actualType)
                    }
                }
        }
    }

/**
 * The run's outcome as something the UI can render, or `null` when there is nothing to say.
 *
 * **Two of the four outcomes are not failures.** [SyncOutcome.Success] obviously; and
 * [SyncOutcome.NotConfigured] deliberately — an app that has never been connected has not failed at
 * anything, and M4.12's setup screen is the answer rather than a red banner on first launch.
 *
 * The mapping groups by **what the user can do**, which is why it is not one-to-one with
 * [NotionError]: a 429 and a 502 both mean *wait*, and a 400 and an unparseable 200 both mean *this
 * build is wrong about the API*. See [SyncFailure].
 */
val SyncOutcome.failure: SyncFailure?
    get() =
        when (this) {
            is SyncOutcome.Success -> null
            SyncOutcome.NotConfigured -> null
            is SyncOutcome.MappingBroken -> SyncFailure.MappingBroken(problems.map(NotionMappingProblem::asIssue))
            is SyncOutcome.Failed -> error.asFailure()
        }

/**
 * The same question, asked of the wire error.
 *
 * Split out so both `when`s stay one line per case, exactly as `SyncWorker` splits
 * [isWorthRetrying]. A new [NotionError] makes this stop compiling, which is the point of that set
 * being closed.
 */
private fun NotionError.asFailure(): SyncFailure =
    when (this) {
        NotionError.NotConnected -> SyncFailure.NotConnected
        NotionError.Unauthorized -> SyncFailure.TokenRejected
        NotionError.Forbidden -> SyncFailure.MissingCapability
        NotionError.MissingOrNotShared -> SyncFailure.DatabaseNotShared
        is NotionError.RateLimited -> SyncFailure.NotionUnavailable
        is NotionError.Server -> SyncFailure.NotionUnavailable
        is NotionError.Network -> SyncFailure.Offline
        is NotionError.Invalid -> SyncFailure.AppOutOfDate
        is NotionError.Malformed -> SyncFailure.AppOutOfDate
    }

/**
 * A validation problem, restated in the domain's terms.
 *
 * The value classes stop here: `NotionPropertyId` belongs to `data/preferences`, and above this
 * layer a property id is an opaque string nothing parses.
 */
private fun NotionMappingProblem.asIssue(): MappingIssue =
    when (this) {
        is NotionMappingProblem.DayKeyMissing -> MappingIssue.DayKeyMissing(id.value)
        is NotionMappingProblem.DayKeyRetyped -> MappingIssue.DayKeyRetyped(id.value, name, actualType)
        is NotionMappingProblem.HabitMissing -> MappingIssue.HabitMissing(id.value)
        is NotionMappingProblem.HabitRetyped -> MappingIssue.HabitRetyped(id.value, name, actualType)
        is NotionMappingProblem.MeasureMissing -> MappingIssue.MeasureMissing(id.value)
        is NotionMappingProblem.MeasureRetyped -> MappingIssue.MeasureRetyped(id.value, name, actualType)
        is NotionMappingProblem.WrongDataSource -> MappingIssue.WrongDataSource
    }
