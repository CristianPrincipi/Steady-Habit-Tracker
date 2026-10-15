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
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
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
     * Asks for a sync as soon as the network allows, **because a person asked**.
     *
     * For pull-to-refresh and M4.13's *Sync now*. **Not for a check-off**: `CLAUDE.md` §3.5 asks for
     * writes to be batched and coalesced rather than fired one per checkbox tap, and the day stays
     * `PENDING_PUSH` until some run picks it up. **Not for app-open either** — see [syncOnAppOpen].
     */
    fun syncNow()

    /**
     * Asks for a sync because the app came to the front — M14.10.
     *
     * ⚠⚠ **A separate request from [syncNow], under its own unique name, and both halves of that
     * matter.** ADR-057 left the warning for whoever built this: [observeQueued] is what turns a held
     * request into *"will sync when you're back online"*, and that line is only honest for a request
     * someone is waiting on. Opening the app offline is the app's normal condition (`CLAUDE.md` §1),
     * so an app-open request that counted would put the line up on every offline launch — the
     * standing nag the user rejected. And [observeRequestedRunning] leaves it out, so the pull
     * indicator does not spin on every launch (ADR-125: a sync the app starts does not draw).
     *
     * `KEEP`, like [syncNow]: coming back to the app twice in a second is one sync.
     */
    fun syncOnAppOpen()

    /**
     * Asks for a sync about a minute after a check-off made **on a home-screen widget** — M15.11.
     *
     * ⭐ **The one check-off that asks for a sync, and the delay is what keeps §3.5 true.** A check-off
     * in the app reaches Notion on the next app-open or periodic run; a widget exists so the app is
     * never opened, which leaves only the periodic run — and the phone's battery saver decides when
     * that is. The user's ruling was to push about a minute after the tap. The minute is the batching
     * `CLAUDE.md` §3.5 asks for: ticking three habits in a row is one request, not three.
     *
     * `KEEP`, so the first tap of a run of them sets the time and the rest ride along. A tap that
     * lands while that sync is already **running** is dropped rather than queued, and nothing is lost
     * by it: its day stays `PENDING_PUSH` (`SyncEngine` checks for an edit made mid-push) and goes on
     * the next run. Silent like [syncOnAppOpen], and for its reason — nobody is looking at the app.
     */
    fun syncAfterWidgetCheckOff()

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

    /**
     * ⭐ Whether a sync **someone asked for** is running — the pull indicator, and nothing else.
     *
     * ADR-125's ruling: pull-to-refresh draws because *"the distinction is the finger"*; background
     * and periodic syncs stay silent. [observeRunning] could not say that, since it answers for every
     * run, and until M14.10 the gap hid because a periodic run rarely lands while the user is looking.
     * A sync on every app open would have made it land every time.
     *
     * ⚠ A requested run that is **waiting for another run to finish** (`SyncEngine` runs one at a
     * time) counts as running here: WorkManager has started it, and the user's sync really is under
     * way — it simply begins with a wait.
     */
    fun observeRequestedRunning(): Flow<Boolean>

    /**
     * ⭐ Whether an **on-demand** sync has been asked for and has not run yet.
     *
     * Not a status on its own — a request that is about to be picked up looks identical to one the
     * `CONNECTED` constraint is holding, and WorkManager exposes no "this constraint is currently
     * unmet" flag to tell them apart. It becomes
     * [com.cristianprincipi.habits.domain.model.SyncStatus.WaitingForNetwork] only when
     * `SyncStatusRepositoryImpl` puts it beside
     * [com.cristianprincipi.habits.core.network.ConnectivityObserver.isOnline]: enqueued **and**
     * offline is a run that provably cannot start.
     *
     * **The periodic schedule is deliberately not counted.** It sits `ENQUEUED` for the whole
     * fifteen minutes between runs, online or off, so including it would light the waiting state up
     * permanently the moment a user turned off their wifi — which is the nagging M4.16 was told not
     * to do. Only [syncNow] means somebody is actually waiting for an answer.
     */
    fun observeQueued(): Flow<Boolean>
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

        override fun syncNow() = enqueueOneRun(ON_DEMAND_WORK)

        override fun syncOnAppOpen() = enqueueOneRun(APP_OPEN_WORK)

        override fun syncAfterWidgetCheckOff() = enqueueOneRun(WIDGET_CHECK_OFF_WORK, delay = WIDGET_CHECK_OFF_DELAY)

        private fun enqueueOneRun(
            uniqueName: String,
            delay: Duration = Duration.ZERO,
        ) {
            val request =
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF.toJavaDuration())
                    .setInitialDelay(delay.toJavaDuration())
                    .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueName,
                // KEEP, so a jabbed refresh button or a quick back-and-forth into the app is one
                // sync rather than a queue of them. Nothing is lost by dropping the extra request: a
                // day owed stays `PENDING_PUSH` until some run takes it, and the periodic schedule is
                // right there.
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        /**
         * Every unique name, folded into one boolean.
         *
         * Asked for separately because `getWorkInfosForUniqueWorkFlow` takes one name, and any of
         * them can be the run in progress: the periodic schedule fires on its own, and [syncNow],
         * [syncOnAppOpen] and [syncAfterWidgetCheckOff] enqueue beside it rather than replacing it.
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
                        workManager.getWorkInfosForUniqueWorkFlow(APP_OPEN_WORK),
                        workManager.getWorkInfosForUniqueWorkFlow(WIDGET_CHECK_OFF_WORK),
                    ) { periodic, onDemand, appOpen, widget ->
                        (periodic + onDemand + appOpen + widget).any { it.state == WorkInfo.State.RUNNING }
                    },
                )
            }.distinctUntilChanged()

        /** [ON_DEMAND_WORK] only. `getInstance` on collection, for [observeRunning]'s reason. */
        override fun observeRequestedRunning(): Flow<Boolean> =
            flow {
                val workManager = WorkManager.getInstance(context)
                emitAll(
                    workManager.getWorkInfosForUniqueWorkFlow(ON_DEMAND_WORK).map { infos ->
                        infos.any { it.state == WorkInfo.State.RUNNING }
                    },
                )
            }.distinctUntilChanged()

        /**
         * One unique name, and only the not-yet-run states.
         *
         * `ENQUEUED` covers both things worth calling queued: a request waiting for its constraint,
         * and one waiting out a backoff after `Result.retry()`. `BLOCKED` cannot happen — nothing in
         * this app chains work — but it is the other unfinished state, and leaving it out would be a
         * silent hole if one is ever added.
         *
         * `getInstance` on collection, for the initialisation-order reason [observeRunning]
         * documents.
         */
        override fun observeQueued(): Flow<Boolean> =
            flow {
                val workManager = WorkManager.getInstance(context)
                emitAll(
                    workManager.getWorkInfosForUniqueWorkFlow(ON_DEMAND_WORK).map { infos ->
                        infos.any {
                            it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED
                        }
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

        // Internal rather than private so `AppOpenSyncDeviceTest` can read the real queue by name.
        internal companion object {
            /**
             * 15 minutes, which is also WorkManager's floor for periodic work.
             *
             * `CLAUDE.md` §9.10 says 15–30; the floor is not worth fighting, and while the app is in
             * use the real cadence is set by app-open (M14.10) and pull-to-refresh, not by this.
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

            /**
             * How long [syncAfterWidgetCheckOff] waits: long enough to gather a run of taps into one
             * push, short enough that Notion is right by the time anyone looks there.
             */
            val WIDGET_CHECK_OFF_DELAY = 1.minutes

            const val PERIODIC_WORK = "notion-sync-periodic"
            const val ON_DEMAND_WORK = "notion-sync-now"
            const val APP_OPEN_WORK = "notion-sync-app-open"
            const val WIDGET_CHECK_OFF_WORK = "notion-sync-widget-check-off"
        }
    }

@Module
@InstallIn(SingletonComponent::class)
interface SyncModule {
    @Binds
    @Singleton
    fun bindSyncScheduler(impl: WorkManagerSyncScheduler): SyncScheduler
}
