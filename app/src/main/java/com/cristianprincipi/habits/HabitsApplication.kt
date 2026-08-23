package com.cristianprincipi.habits

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.cristianprincipi.habits.data.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
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

    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(workerFactory)
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
}
