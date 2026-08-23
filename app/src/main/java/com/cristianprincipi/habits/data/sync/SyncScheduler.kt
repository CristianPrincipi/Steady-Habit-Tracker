package com.cristianprincipi.habits.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * How a sync gets asked for. **The only path to WorkManager in this app.**
 *
 * An interface so nothing above the data layer ever holds a `WorkManager`: M4.13's manual retry and
 * M4.11's sync state both need a way in that is not a schedule, and a ViewModel reaching for
 * `WorkManager.getInstance(context)` would be a `Context` in a ViewModel (`CLAUDE.md` §6) and an
 * untestable one at that.
 */
interface SyncScheduler {
    /**
     * Makes sure the background schedule exists. Idempotent — safe to call on every launch, and
     * meant to be.
     */
    fun ensureScheduled()

    /**
     * Asks for a sync as soon as the network allows.
     *
     * For app-open and for the manual refresh M4.13 offers. **Not for a check-off** — `CLAUDE.md`
     * §3.5 asks for writes to be batched and coalesced rather than fired one per checkbox tap, and
     * the day stays `PENDING_PUSH` until some run picks it up.
     */
    fun syncNow()

    /**
     * Whether a run is happening **right now**.
     *
     * The "syncing" half of [com.cristianprincipi.habits.domain.model.SyncStatus], and the reason
     * this interface exists rather than the repository holding a `WorkManager`: WorkManager is the
     * only thing that knows, and it needs a `Context` to ask.
     *
     * Queued is not running. A periodic request sits `ENQUEUED` between runs and a retry sits
     * `ENQUEUED` through its backoff — reporting either as "syncing" would leave a spinner turning
     * for fifteen minutes at a time.
     */
    fun observeRunning(): Flow<Boolean>
}

@Singleton
class WorkManagerSyncScheduler
    @Inject
    constructor(
        // The Context rather than a WorkManager, and the reason is initialisation order.
        // `WorkManager.getInstance` is what triggers on-demand initialisation, which reads
        // `HabitsApplication.workManagerConfiguration`, which reads a field Hilt injects in the same
        // pass that would build this class. Resolving it inside the methods instead means the first
        // touch happens after `super.onCreate()` has returned and every field is set.
        @param:ApplicationContext private val context: Context,
    ) : SyncScheduler {
        override fun ensureScheduled() {
            val request =
                PeriodicWorkRequestBuilder<SyncWorker>(PERIOD.toJavaDuration())
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF.toJavaDuration())
                    .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                // UPDATE rather than KEEP: a changed interval or constraint should take effect on
                // the next launch. KEEP would pin whatever the first install enqueued, forever.
                // REPLACE would restart the period on every launch, so a user who opens the app
                // often would never reach the end of one.
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        override fun syncNow() {
            val request =
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF.toJavaDuration())
                    .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                ON_DEMAND_WORK,
                // KEEP, so a burst of check-offs or a jabbed refresh button is one sync rather than
                // a queue of them. Nothing is lost by dropping the extra request: a day owed stays
                // `PENDING_PUSH` until some run takes it, and the periodic schedule is right there.
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        /**
         * Both unique names, folded into one boolean.
         *
         * The two are asked for separately because `getWorkInfosForUniqueWorkFlow` takes one name,
         * and either can be the run in progress: the periodic schedule fires on its own, and
         * [syncNow] enqueues beside it rather than replacing it.
         *
         * **`getInstance` is called on collection, not when the flow is built.** The same
         * initialisation-order trap the constructor comment describes — a caller could otherwise
         * construct this flow during Hilt's own graph construction and trigger WorkManager's
         * on-demand initialisation before `HabitsApplication` has finished starting.
         */
        override fun observeRunning(): Flow<Boolean> =
            flow {
                val workManager = WorkManager.getInstance(context)
                emitAll(
                    combine(
                        workManager.getWorkInfosForUniqueWorkFlow(PERIODIC_WORK),
                        workManager.getWorkInfosForUniqueWorkFlow(ON_DEMAND_WORK),
                    ) { periodic, onDemand ->
                        (periodic + onDemand).any { it.state == WorkInfo.State.RUNNING }
                    },
                )
            }.distinctUntilChanged()

        /**
         * A connection, and nothing else.
         *
         * `CLAUDE.md` §9.10 takes "on any connection" as the default rather than unmetered-only —
         * a habit sync is a handful of small JSON requests, and a user who checks off on mobile data
         * expects it to arrive. M11.10 offers the toggle if they want otherwise.
         *
         * **No battery or idle constraint.** Both would let a device that is used all day sync only
         * overnight, which is the opposite of what a daily habit app wants.
         */
        private val constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        private companion object {
            /**
             * 15 minutes, which is also WorkManager's floor for periodic work.
             *
             * `CLAUDE.md` §9.10 says 15–30; the floor is not worth fighting, and the real cadence is
             * set by app-open and by check-offs, not by this.
             */
            val PERIOD = 15.minutes

            /**
             * The first retry after a failed run, doubling from there.
             *
             * Coarser than `core/network`'s retry, and deliberately so: that one handles a 429 or a
             * dropped socket **inside** a run, honouring Notion's own `Retry-After`. This one only
             * fires once the run as a whole has given up, so it is measured in minutes of not
             * bothering the user's battery rather than in seconds of politeness to the API.
             */
            val BACKOFF = 30.seconds

            const val PERIODIC_WORK = "notion-sync-periodic"
            const val ON_DEMAND_WORK = "notion-sync-now"
        }
    }

@Module
@InstallIn(SingletonComponent::class)
interface SyncModule {
    @Binds
    @Singleton
    fun bindSyncScheduler(impl: WorkManagerSyncScheduler): SyncScheduler
}
