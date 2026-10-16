package com.cristianprincipi.habits.data.sync

import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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
     * Asks for a sync **now**, because a habit was checked off **on a home-screen widget** — M15.11.
     *
     * ⭐ **The one check-off that asks for a sync.** A check-off in the app reaches Notion on the next
     * app-open or periodic run; a widget exists so the app is never opened, which leaves only the
     * periodic run — and the phone's battery saver decides when that is.
     *
     * ⚠⚠ **Now, not "about a minute after the tap" — that was the first version, and the phone
     * refused it (ADR-152).** A delayed request needs the system to start the app again later, and
     * the user's phone freezes a backgrounded app within seconds and holds its jobs: the minute
     * passed, nothing ran, and the tick reached Notion only when something next woke the app. The one
     * moment the app is certainly awake is the tap itself, so the run starts then — expedited on API
     * 31+, which asks the system to keep the app running until the run ends.
     *
     * ⭐ **Never dropped, and still batched** — see [widgetCheckOffPolicy]. A tap while a run is going
     * queues **one** run after it; every further tap rides on that one. Three ticks in a row are two
     * runs at most, which is the coalescing `CLAUDE.md` §3.5 asks for, and the last tick is never the
     * one left behind. Silent like [syncOnAppOpen], and for its reason — nobody is looking at the app.
     *
     * **Suspends until WorkManager has stored the request**, so it is on disk before the tap's
     * broadcast ends and the app can be frozen again.
     */
    suspend fun syncAfterWidgetCheckOff()

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

        override suspend fun syncAfterWidgetCheckOff() {
            val workManager = WorkManager.getInstance(context)
            val states = workManager.getWorkInfosForUniqueWorkFlow(WIDGET_CHECK_OFF_WORK).first().map(WorkInfo::state)
            workManager
                .enqueueUniqueWork(WIDGET_CHECK_OFF_WORK, widgetCheckOffPolicy(states), oneRun(expedited = true))
                .await()
        }

        private fun enqueueOneRun(uniqueName: String) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueName,
                // KEEP, so a jabbed refresh button or a quick back-and-forth into the app is one
                // sync rather than a queue of them. Nothing is lost by dropping the extra request: a
                // day owed stays `PENDING_PUSH` until some run takes it, and the periodic schedule is
                // right there.
                ExistingWorkPolicy.KEEP,
                oneRun(),
            )
        }

        /**
         * One sync, as soon as there is a connection.
         *
         * @param expedited asks the system to start it at once and keep the app running until it
         *   ends — **API 31+ only**. Below that, WorkManager runs expedited work as a foreground
         *   service, which needs the notification v1 does not have (`SyncWorker`'s KDoc), so there it
         *   stays an ordinary request. Out of quota, it still runs, as an ordinary one.
         */
        private fun oneRun(expedited: Boolean = false): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF.toJavaDuration())
                .apply {
                    if (expedited && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    }
                }.build()

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

            const val PERIODIC_WORK = "notion-sync-periodic"
            const val ON_DEMAND_WORK = "notion-sync-now"
            const val APP_OPEN_WORK = "notion-sync-app-open"
            const val WIDGET_CHECK_OFF_WORK = "notion-sync-widget-check-off"
        }
    }

/**
 * How a widget tap's request meets whatever is already under
 * [WorkManagerSyncScheduler.WIDGET_CHECK_OFF_WORK], given the state of every request under that
 * name, finished ones included — M15.11, ADR-152.
 *
 * ⭐ **The rule is that every tap gets a run that starts after its write**, and the write has already
 * landed by the time this is asked:
 *
 * - **Nothing running → `REPLACE`.** Either nothing is unfinished, or a request is waiting — for the
 *   network, or out a retry's backoff. A fresh, expedited request now is at least as good as either.
 * - **A run going, nothing after it → `APPEND_OR_REPLACE`.** ⚠ The running one may have read the day
 *   before this tap wrote it, so one more run goes after it. `KEEP` here was the first version's
 *   bug: it dropped the request, and the last tick of a row of them waited for the app to be opened.
 * - **A run going, one already after it → `KEEP`.** That one has not started, so it will read this
 *   tap's write. Appending again would only add a run.
 *
 * Losing a race costs nothing. A run that ends before the append lands leaves a request that starts
 * at once; a waiting request that starts before `REPLACE` lands is cancelled mid-run, which the engine
 * is built to survive (ADR-047), and the replacement runs straight after it.
 */
internal fun widgetCheckOffPolicy(states: Collection<WorkInfo.State>): ExistingWorkPolicy =
    when {
        WorkInfo.State.RUNNING !in states -> ExistingWorkPolicy.REPLACE
        WorkInfo.State.BLOCKED in states -> ExistingWorkPolicy.KEEP
        else -> ExistingWorkPolicy.APPEND_OR_REPLACE
    }

@Module
@InstallIn(SingletonComponent::class)
interface SyncModule {
    @Binds
    @Singleton
    fun bindSyncScheduler(impl: WorkManagerSyncScheduler): SyncScheduler
}
