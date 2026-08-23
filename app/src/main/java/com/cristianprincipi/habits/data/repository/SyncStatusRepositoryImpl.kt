package com.cristianprincipi.habits.data.repository

import com.cristianprincipi.habits.data.local.dao.HabitDao
import com.cristianprincipi.habits.data.sync.DiscardedEdit
import com.cristianprincipi.habits.data.sync.DiscardedEditStore
import com.cristianprincipi.habits.data.sync.LastSyncFailureStore
import com.cristianprincipi.habits.data.sync.SyncScheduler
import com.cristianprincipi.habits.data.sync.SyncSide
import com.cristianprincipi.habits.domain.model.DiscardedEditNotice
import com.cristianprincipi.habits.domain.model.EditSide
import com.cristianprincipi.habits.domain.model.SyncStatus
import com.cristianprincipi.habits.domain.repository.SettingsRepository
import com.cristianprincipi.habits.domain.repository.SyncStatusRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SyncStatusRepository] over the three things that between them know the answer.
 *
 * None of them is a sync status on its own, and no single one could be:
 *
 * - **when the data was last correct** — [SettingsRepository.observeLastSyncedAt], written by
 *   [com.cristianprincipi.habits.data.sync.SyncEngine] on success only;
 * - **whether a run is happening now** — WorkManager, reached through [SyncScheduler] so that
 *   nothing above this class needs a `Context`;
 * - **how the last run got on** — [LastSyncFailureStore], because the run itself happened in a
 *   background process that is long gone by the time anyone looks.
 *
 * ### Running beats failed
 *
 * The order in the `when` below is the whole behaviour of this class, and it is deliberate. A retry
 * is a run in progress, and the last failure is still on disk while it happens — it is only replaced
 * when the new run finishes. Showing the stale error over the top of a live attempt would tell the
 * user something is broken at the exact moment it is being fixed.
 *
 * ### Nothing here waits for anything
 *
 * `CLAUDE.md` §1: Room is the source of truth for the UI. All three sources emit their current value
 * immediately — a DataStore flow its stored value or its absence, WorkManager its current work info
 * — so a collector gets a status without a round trip, and a check-off never touches this at all.
 */
@Singleton
class SyncStatusRepositoryImpl
    @Inject
    constructor(
        private val settings: SettingsRepository,
        private val lastFailure: LastSyncFailureStore,
        private val scheduler: SyncScheduler,
        private val discardedEdits: DiscardedEditStore,
        private val habitDao: HabitDao,
    ) : SyncStatusRepository {
        override fun observeStatus(): Flow<SyncStatus> =
            combine(
                settings.observeLastSyncedAt(),
                lastFailure.failure,
                scheduler.observeRunning(),
            ) { lastSyncedAt, failure, running ->
                when {
                    running -> SyncStatus.Syncing(lastSyncedAt)
                    failure != null -> SyncStatus.Failed(failure, lastSyncedAt)
                    else -> SyncStatus.Idle(lastSyncedAt)
                }
                // Three sources, and any of them can emit without changing the answer: WorkManager
                // re-emits a work info on every state transition, most of which are between two kinds
                // of not-running. Without this a status bar would recompose on each one.
            }.distinctUntilChanged()

        override fun requestSync() {
            scheduler.syncNow()
        }

        /**
         * The stored notices, with their habit ids turned into the names those habits have **now**.
         *
         * The join is here rather than at write time on purpose (ADR-038): names change upstream,
         * and a notice written last week should read with today's name. `combine` rather than a
         * one-shot read, so a rename arriving on the next sync updates a notice already on screen.
         *
         * A habit whose column has since been deleted has no name left to find and drops out of the
         * list — which is why the count travels separately, so the sentence can still say how many
         * habits were affected.
         */
        override fun observeDiscardedEdits(): Flow<List<DiscardedEditNotice>> =
            combine(discardedEdits.edits, habitDao.observeAll()) { edits, habits ->
                // The Notion column name, which is what the habit is called everywhere in this app
                // — habits have no name of their own (ADR-010: they are columns, not rows).
                val namesById = habits.associate { habit -> habit.id to habit.notionPropertyName }
                edits.map { edit -> edit.toNotice(namesById) }
            }.distinctUntilChanged()

        override suspend fun dismissDiscardedEdits() {
            discardedEdits.clear()
        }
    }

private fun DiscardedEdit.toNotice(namesById: Map<String, String>): DiscardedEditNotice =
    DiscardedEditNotice(
        date = LocalDate.ofEpochDay(epochDay),
        habitNames = habitIds.mapNotNull(namesById::get).sorted(),
        habitCount = habitIds.size,
        discardedSide =
            when (discardedSide) {
                SyncSide.LOCAL -> EditSide.Local
                SyncSide.REMOTE -> EditSide.Remote
            },
        resolvedAt = Instant.ofEpochMilli(resolvedAtEpochMillis),
    )
