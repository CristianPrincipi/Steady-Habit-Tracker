package com.cristianprincipi.habits.data.repository

import com.cristianprincipi.habits.core.network.ConnectivityObserver
import com.cristianprincipi.habits.data.local.dao.HabitDao
import com.cristianprincipi.habits.data.local.dao.MeasureDao
import com.cristianprincipi.habits.data.local.entity.MeasureEntity
import com.cristianprincipi.habits.data.sync.DiscardedEdit
import com.cristianprincipi.habits.data.sync.DiscardedEditStore
import com.cristianprincipi.habits.data.sync.LastSyncFailureStore
import com.cristianprincipi.habits.data.sync.SyncScheduler
import com.cristianprincipi.habits.data.sync.SyncSide
import com.cristianprincipi.habits.domain.model.DiscardedEditNotice
import com.cristianprincipi.habits.domain.model.DiscardedNumberNotice
import com.cristianprincipi.habits.domain.model.EditSide
import com.cristianprincipi.habits.domain.model.SyncFailure
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
 * [SyncStatusRepository] over the four things that between them know the answer.
 *
 * None of them is a sync status on its own, and no single one could be:
 *
 * - **when the data was last correct** — [SettingsRepository.observeLastSyncedAt], written by
 *   [com.cristianprincipi.habits.data.sync.SyncEngine] on success only;
 * - **whether a run is happening now** — WorkManager, reached through [SyncScheduler] so that
 *   nothing above this class needs a `Context`;
 * - **how the last run got on** — [LastSyncFailureStore], because the run itself happened in a
 *   background process that is long gone by the time anyone looks;
 * - **whether a run that was asked for can even start** — [SyncScheduler.observeQueued] beside
 *   [ConnectivityObserver.isOnline], which is M4.16's addition and the one combination neither
 *   source could make alone.
 *
 * ### Running beats failed
 *
 * The order in the `when` below is the whole behaviour of this class, and it is deliberate. A retry
 * is a run in progress, and the last failure is still on disk while it happens — it is only replaced
 * when the new run finishes. Showing the stale error over the top of a live attempt would tell the
 * user something is broken at the exact moment it is being fixed.
 *
 * ### ⭐ Waiting beats one failure, and only one
 *
 * A queued run outranks [SyncFailure.Offline] and nothing else, because it supersedes **exactly the
 * failure it would itself resolve**. The case is real, and it is the one a user hits hardest: a run
 * dies on a flaky connection, they tap "Sync now", and the request is held — so the banner goes on
 * reading *"check your connection and try again"* over a job that is already waiting, and every
 * further tap does nothing at all. "Will sync when you're back online" is both truer and calmer.
 *
 * Every other failure still wins. A rejected token, a broken mapping, an unshared database: none of
 * them is fixed by the network returning, the queued run will hit the same wall, and burying an
 * actionable error under a reassuring one would be the worse lie.
 *
 * ### Nothing here waits for anything
 *
 * `CLAUDE.md` §1: Room is the source of truth for the UI. All four sources emit their current value
 * immediately — a DataStore flow its stored value or its absence, WorkManager its current work info,
 * and `ConnectivityObserver` the current network *including when there is none* (ADR-041, which is
 * the whole reason that interface documents its first emission) — so a collector gets a status
 * without a round trip, and a check-off never touches this at all.
 */
@Singleton
class SyncStatusRepositoryImpl
    @Inject
    // The four sources the class KDoc lists, the notices, and the two tables their ids are named
    // from — habits since M4.13, measures since M16.2. Bundling the two DAOs behind a lookup class
    // would add an indirection whose only job is a lint count.
    @Suppress("LongParameterList")
    constructor(
        private val settings: SettingsRepository,
        private val lastFailure: LastSyncFailureStore,
        private val scheduler: SyncScheduler,
        private val connectivity: ConnectivityObserver,
        private val discardedEdits: DiscardedEditStore,
        private val habitDao: HabitDao,
        private val measureDao: MeasureDao,
    ) : SyncStatusRepository {
        /**
         * A run has been asked for and provably cannot start.
         *
         * Folded here rather than inside [observeStatus]'s `combine` because neither half means
         * anything alone: enqueued-and-online is a sync about to happen, and offline-with-nothing-
         * owed is this app's normal condition (`CLAUDE.md` §1). Only the conjunction is a state
         * worth putting on a screen.
         */
        private val waitingForNetwork: Flow<Boolean> =
            combine(scheduler.observeQueued(), connectivity.isOnline) { queued, online ->
                queued && !online
            }.distinctUntilChanged()

        override fun observeStatus(): Flow<SyncStatus> =
            combine(
                settings.observeLastSyncedAt(),
                lastFailure.failure,
                scheduler.observeRunning(),
                waitingForNetwork,
            ) { lastSyncedAt, failure, running, waiting ->
                when {
                    running -> {
                        SyncStatus.Syncing(lastSyncedAt)
                    }

                    waiting && failure.isResolvedByReconnecting() -> {
                        SyncStatus.WaitingForNetwork(lastSyncedAt)
                    }

                    failure != null -> {
                        SyncStatus.Failed(failure, lastSyncedAt)
                    }

                    else -> {
                        SyncStatus.Idle(lastSyncedAt)
                    }
                }
                // Four sources, and any of them can emit without changing the answer: WorkManager
                // re-emits a work info on every state transition, most of which are between two kinds
                // of not-running. Without this a status bar would recompose on each one.
            }.distinctUntilChanged()

        override fun requestSync() {
            scheduler.syncNow()
        }

        override fun requestAppOpenSync() {
            scheduler.syncOnAppOpen()
        }

        override suspend fun requestWidgetCheckOffSync() {
            scheduler.syncAfterWidgetCheckOff()
        }

        override fun observeRequestedSyncRunning(): Flow<Boolean> = scheduler.observeRequestedRunning()

        /**
         * The stored notices, with their habit ids turned into the names those habits have **now**.
         *
         * The join is here rather than at write time on purpose (ADR-038): names change upstream,
         * and a notice written last week should read with today's name. `combine` rather than a
         * one-shot read, so a rename arriving on the next sync updates a notice already on screen.
         *
         * A habit whose column has since been deleted has no name left to find and drops out of the
         * list — which is why the count travels separately, so the sentence can still say how many
         * habits were affected. A number's notice (M16.2) is joined the same way, to the measure's
         * name and unit as they are now.
         */
        override fun observeDiscardedEdits(): Flow<List<DiscardedEditNotice>> =
            combine(discardedEdits.edits, habitDao.observeAll(), measureDao.observeAll()) { edits, habits, measures ->
                // The Notion column name, which is what the habit is called everywhere in this app
                // — habits have no name of their own (ADR-010: they are columns, not rows).
                val namesById = habits.associate { habit -> habit.id to habit.notionPropertyName }
                val measuresById = measures.associateBy(MeasureEntity::id)
                edits.map { edit -> edit.toNotice(namesById, measuresById) }
            }.distinctUntilChanged()

        override suspend fun dismissDiscardedEdits() {
            discardedEdits.clear()
        }
    }

/**
 * Whether a waiting run would make this failure go away by itself.
 *
 * `null` — no failure at all — counts, and is the ordinary case: the user was simply offline when
 * they tapped. [SyncFailure.Offline] counts because it is the same problem stated one run earlier.
 * Nothing else does; the class KDoc says why.
 *
 * A `when` over the sealed type rather than `== Offline`, so that adding a way to fail stops this
 * compiling and forces a decision, instead of silently defaulting to "the error wins".
 */
private fun SyncFailure?.isResolvedByReconnecting(): Boolean =
    when (this) {
        null, SyncFailure.Offline -> true

        SyncFailure.NotConnected,
        SyncFailure.TokenRejected,
        SyncFailure.MissingCapability,
        SyncFailure.DatabaseNotShared,
        SyncFailure.NotionUnavailable,
        SyncFailure.AppOutOfDate,
        is SyncFailure.MappingBroken,
        -> false
    }

private fun DiscardedEdit.toNotice(
    namesById: Map<String, String>,
    measuresById: Map<String, MeasureEntity>,
): DiscardedEditNotice =
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
        number =
            number?.let { number ->
                val measure = measuresById[number.measureId]
                DiscardedNumberNotice(
                    measureName = measure?.notionPropertyName,
                    unit = measure?.unit.orEmpty(),
                    discardedValue = number.discardedValue,
                    keptValue = number.keptValue,
                )
            },
    )
