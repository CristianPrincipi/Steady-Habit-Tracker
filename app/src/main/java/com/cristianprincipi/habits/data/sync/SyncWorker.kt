package com.cristianprincipi.habits.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cristianprincipi.habits.core.widget.WidgetRefresher
import com.cristianprincipi.habits.core.widget.refreshQuietly
import com.cristianprincipi.habits.data.remote.notion.NotionError
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Runs [SyncEngine] on WorkManager's schedule. **The whole class is four lines of behaviour.**
 *
 * That is the point: everything hard already happened. The engine is idempotent and resumable
 * (ADR-047), it never throws, and it answers with a [SyncOutcome]. All this adds is the translation
 * into WorkManager's vocabulary — and the one decision worth making, which is
 * [SyncOutcome.isWorthRetrying].
 *
 * **`CoroutineWorker`, so cancellation works.** WorkManager stops a worker by cancelling its job,
 * and the engine is written to be killed mid-run — a partial pull leaves the local database valid
 * and the watermark is a correct resume point.
 *
 * **Not expedited, and that is a deviation from `Plan.md` M4.10 worth reading.** Expedited work
 * below API 31 runs as a **foreground service**, which needs a visible notification and therefore a
 * notification channel — and v1 deliberately has neither (ADR-009: reminders are post-v1, no
 * `POST_NOTIFICATIONS`, no channel). `minSdk` is 26, so that path is live for real users. The thing
 * expedited would buy is starting a few seconds sooner, and nothing waits on this: every screen
 * reads Room, and a check-off has already succeeded locally before any of this runs (`CLAUDE.md`
 * §1). An ordinary one-time request that starts within seconds of the app opening is the same
 * experience without the notification the user never asked for.
 *
 * ⚠ **One exception since M15.11 (ADR-152): a widget check-off's run is expedited on API 31+**, where
 * expedited work needs no notification. That run starts with the app in the background, and on the
 * user's phone an ordinary request from there waited until the app was next opened.
 */
@HiltWorker
class SyncWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted parameters: WorkerParameters,
        private val syncEngine: SyncEngine,
        private val lastFailure: LastSyncFailureStore,
        private val widgets: WidgetRefresher,
    ) : CoroutineWorker(appContext, parameters) {
        override suspend fun doWork(): Result {
            val outcome = syncEngine.sync()
            // Recorded before the Result is decided, and for every outcome including the good ones.
            // WorkManager's own state says whether a run happened; only this says how it went, and
            // a failure that reached no further than this method is a failure nobody can be told
            // about (`CLAUDE.md` §3.5). A success clears whatever the last failure was.
            lastFailure.record(outcome)
            // M15.6c. This is the app's only background sync boundary — the periodic, on-demand and
            // app-open paths all enqueue this worker (`SyncScheduler`) — so one call here covers
            // pull-to-refresh, opening the app and the background pass alike.
            if (outcome.changedLocalData) widgets.refreshQuietly()
            return if (outcome.isWorthRetrying) Result.retry() else Result.success()
        }
    }

/**
 * Whether this run changed anything a home-screen widget could be drawing (M15.6c).
 *
 * ⭐ **A property of the outcome rather than a branch inside `doWork`, for [isWorthRetrying]'s
 * reason**: the whole rule is then testable on the JVM without WorkManager, a device or a fake
 * worker, and the worker keeps having nothing to get wrong.
 *
 * **Only a pull can move a pixel.** A push-only success sent local state *to* Notion, so every
 * widget is already drawing it; `MappingBroken` pulls and pushes nothing by definition; `Failed`
 * and `NotConfigured` change nothing at all. Waking every widget on those is work that cannot
 * affect what anyone sees, and `updateAll` starts a Glance session per placed widget.
 *
 * ⚠ **`daysPulled > 0` is a proxy and a deliberately loose one.** A pulled day may turn out to hold
 * exactly what Room already had, so this can refresh when nothing visibly changed. That is the
 * cheap direction to be wrong in: the alternative is threading "did any row actually differ" out of
 * the engine, and a redundant repaint costs a background recomposition while a missed one leaves a
 * stale grid on the user's home screen.
 */
val SyncOutcome.changedLocalData: Boolean
    get() =
        when (this) {
            is SyncOutcome.Success -> daysPulled > 0
            SyncOutcome.NotConfigured -> false
            is SyncOutcome.MappingBroken -> false
            is SyncOutcome.Failed -> false
        }

/**
 * Whether waiting and trying again could plausibly change the answer.
 *
 * **`Result.failure()` is never used, and that is deliberate.** In WorkManager it means *give up
 * permanently*, which would cancel a periodic schedule the user never asked to stop. A sync that
 * cannot succeed today is a state for the UI to explain (M4.11, M4.13), not a reason to stop
 * syncing forever — so everything that is not worth retrying comes back as `success` with the
 * reason surfaced elsewhere.
 *
 * The line is drawn at **can time fix it?**:
 *
 * - **Yes** — rate limits, dead sockets, and Notion's own 5xx. This is what the backoff exists for.
 * - **No, it needs a person** — no token, a revoked one, a database that was never shared, a
 *   mapping whose properties have moved. Retrying spends the backoff window relearning the same
 *   thing, and the periodic run is along in fifteen minutes anyway if the user fixes it.
 * - **No, it needs us** — a request Notion called invalid, or a response that would not parse.
 *   Both mean this build is wrong about the API (`docs/notion-api.md` §11), and repeating the
 *   request repeats the mistake.
 */
val SyncOutcome.isWorthRetrying: Boolean
    get() =
        when (this) {
            is SyncOutcome.Success -> false
            SyncOutcome.NotConfigured -> false
            is SyncOutcome.MappingBroken -> false
            is SyncOutcome.Failed -> error.isWorthRetrying
        }

/**
 * The same question, asked of the failure itself.
 *
 * Split out so both `when`s stay one line per case. The order below is the reasoning, and the
 * grouping is deliberate even though ktlint will not let a comment sit between the branches:
 * the first three are temporary by nature and are what the backoff exists for; the next four need
 * a **person** — no token, a revoked one, a database never shared with the connection; the last two
 * need **us**, because this build is wrong about the API and repeating the request repeats it.
 *
 * A new [NotionError] makes this stop compiling, which is the point of the set being closed.
 */
private val NotionError.isWorthRetrying: Boolean
    get() =
        when (this) {
            is NotionError.RateLimited -> true
            is NotionError.Server -> true
            is NotionError.Network -> true
            NotionError.NotConnected -> false
            NotionError.Unauthorized -> false
            NotionError.Forbidden -> false
            NotionError.MissingOrNotShared -> false
            is NotionError.Invalid -> false
            is NotionError.Malformed -> false
        }
