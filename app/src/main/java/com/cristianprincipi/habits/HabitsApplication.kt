package com.cristianprincipi.habits

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.cristianprincipi.habits.data.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.Executors
import javax.inject.Inject

/**
 * Application entry point, Hilt's dependency graph root, and WorkManager's configuration.
 *
 * **It stopped being empty of logic in M4.10, and the reason is worth knowing.** WorkManager builds
 * workers itself, so a `SyncWorker` that needs `SyncEngine` injected can only be built by a factory
 * that knows the graph — [HiltWorkerFactory]. Supplying one means supplying a whole
 * [Configuration], and the only place an app can do that is here.
 *
 * ⚠ **The other half of that is in `AndroidManifest.xml`**, where WorkManager's own initialiser is
 * removed. Without that removal this configuration is built, ignored, and the default factory is
 * used instead — which cannot construct a worker with constructor dependencies. It fails at
 * runtime, in a background job, with a message about the worker class rather than about the
 * manifest. Read the comment there before touching either.
 */
@HiltAndroidApp
class HabitsApplication :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncScheduler: SyncScheduler

    /**
     * ⚠⚠ **The executor is set explicitly, and it is a widget fix rather than a sync one.**
     * WorkManager's default pool is `max(2, min(cpus - 1, 4))` — four threads on this phone. Glance
     * runs **each widget's composition as a WorkManager job that stays alive for a 45-second idle
     * timeout**, so four home-screen widgets occupy the entire default pool, and anything else —
     * another widget's session, or `SyncWorker` — waits for one to expire.
     *
     * That is not theoretical: logcat on the user's phone showed a widget taking **44 seconds** to
     * fill after they picked a habit, with two Glance sessions closing `wasOpen=false` after
     * exactly 45s. See ADR-129 §13.
     *
     * ⭐ **[WORK_THREADS] is sized for sessions, not for throughput.** One thread per concurrently
     * open widget session, plus room for a sync — v1 has seven possible subjects and a user may
     * place the same one twice, so the ceiling is not small. Idle threads in a fixed pool cost
     * almost nothing; a starved widget costs 45 seconds of the user's attention.
     */
    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(workerFactory)
                .setExecutor(Executors.newFixedThreadPool(WORK_THREADS))
                .build()

    /**
     * **After `super.onCreate()`, never before.** Hilt injects the fields above during that call,
     * and [SyncScheduler.ensureScheduled] is the first thing that touches WorkManager — which reads
     * [workManagerConfiguration], which reads [workerFactory].
     *
     * Called on every launch on purpose: the work is unique and the policy is `UPDATE`, so this
     * costs one no-op enqueue and guarantees the schedule exists after an install, an update, a
     * "force stop", or a user clearing the app's data.
     */
    override fun onCreate() {
        super.onCreate()
        syncScheduler.ensureScheduled()
    }

    private companion object {
        /**
         * Enough threads that a home screen full of widgets cannot starve a sync, or each other.
         *
         * ⚠ Not a tuning knob for speed. Nothing here runs faster with more threads — the jobs are
         * mostly *waiting*, because a Glance session holds its thread for a 45-second idle timeout
         * whether or not it is doing anything. This is a concurrency floor, not a throughput one.
         */
        const val WORK_THREADS = 12
    }
}
