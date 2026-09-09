package com.cristianprincipi.habits.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How much of a Notion page this build knows how to read — and therefore whether the history in
 * Room was read by a build that knew less.
 *
 * ### ⚠⚠ The bug this exists for, found on the user's phone at M15.6d
 *
 * The pull is incremental: it asks Notion only for pages edited since
 * `DayDao.latestRemoteEditMillis()`, which is exactly right for keeping up and exactly wrong the
 * day the app learns to read a **new property**. M15.4 taught `applyPage` to store numbers. Every
 * day page older than that already sat under the watermark, so it was never fetched again — and its
 * weight and sleep were never stored, and never would be.
 *
 * The user saw it as a sparkline pinned to the right-hand edge of its widget: two readings, from the
 * only two days edited since the feature shipped, drawn correctly across a month that was empty
 * behind them. ⭐ **Nothing was wrong with the drawing, the query, or the mapping** — the history had
 * simply never been asked for.
 *
 * ⚠ **A fresh install never had this problem**, which is why no test and no first-run walk-through
 * caught it: with an empty `days` table the watermark is null and the first sync pulls everything.
 * Only an install that *upgraded across* the change is affected, and the developer's own device is
 * usually the fresh one.
 *
 * ### ⭐⭐ The rule has two halves, because the trigger has two shapes
 *
 * A build can learn to read more, and **so can a mapping** — and the second is not the rarer case.
 * `CLAUDE.md` §3.4 is emphatic that the mapping is *user data* that changes without warning: the
 * day the user ticks a third number column, its history is in exactly the position Weight and Sleep
 * were in, and no version constant will have moved. Keying the backfill on the build alone would
 * have fixed this once and left the same trap set for the next column.
 *
 * So a backfill is owed when **either**:
 *
 * 1. [CURRENT_PULL_FORMAT] has moved — bump it whenever `applyPage` learns to read a new *kind* of
 *    thing; or
 * 2. the mapping names a property id that no completed pass has ever read.
 *
 * The next sync then runs one full pass — no watermark, every page — and the history catches up.
 * That pass is safe by construction: `applyPage` is idempotent, `applyCompletions` is still gated
 * on `SYNCED` so an unpushed check-off cannot be clobbered, and a measure cannot be edited locally
 * at all.
 *
 * ⚠ **The recorded ids are the last pass's, not everything ever seen**, and that is deliberate.
 * Unmapping a property does not stop Notion filling that column — so a property removed and later
 * put back has real history nobody read, and it earns a fresh pass. Remembering it forever would
 * skip one.
 *
 * ⚠ It is **not** a Room schema version and must not be confused with one. Room's version tracks
 * the shape of the tables; this tracks how much of a *remote* page we bother to look at. The two
 * move together by coincidence, not by rule — a migration that only adds an index changes one and
 * not the other.
 */
@Singleton
class PullFormatStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        /**
         * Whether the next pull should ignore the watermark and re-read every page.
         *
         * **An unreadable store answers `true`**, which is the safe direction: the cost of being
         * wrong is one extra full pass over a database of day pages, and the cost of the other
         * answer is history that silently never arrives. Contrast `LastSyncFailureStore`, which
         * flattens to `null` because its value only drives a banner.
         */
        suspend fun needsBackfill(readableIds: Set<String>): Boolean =
            dataStore.data
                .catch { throwable -> if (throwable is IOException) emit(emptyPreferences()) else throw throwable }
                .map { preferences ->
                    (preferences[FORMAT] ?: NEVER_PULLED) < CURRENT_PULL_FORMAT ||
                        !preferences[READ_IDS].orEmpty().containsAll(readableIds)
                }.first()

        /**
         * Records that a full pass has completed under this build's rules.
         *
         * ⚠ **Called only when the run finished untruncated**, and that matters: Notion's query
         * stops at 10,000 results, and the incremental path survives that because the watermark has
         * advanced to the last page applied. A backfill has no watermark to lean on, so recording a
         * truncated pass would leave the oldest history unread with nothing to say so.
         */
        suspend fun recordBackfill(readableIds: Set<String>) {
            dataStore.edit { preferences ->
                preferences[FORMAT] = CURRENT_PULL_FORMAT
                preferences[READ_IDS] = readableIds
            }
        }

        companion object {
            /**
             * What this build reads from a page.
             *
             * - **1** — the day's date and its habit checkboxes (M4).
             * - **2** — and its numbers: Weight, Sleep (M15.4, backfilled at M15.6e).
             */
            const val CURRENT_PULL_FORMAT = 2

            /** Absent means an install that predates this store, which is exactly format 1. */
            private const val NEVER_PULLED = 1

            private val FORMAT = intPreferencesKey("pull_format")

            private val READ_IDS = stringSetPreferencesKey("pull_read_property_ids")
        }
    }
