package com.cristianprincipi.habits.core.ui

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import com.cristianprincipi.habits.MainActivity
import com.cristianprincipi.habits.core.testing.FreshInstallRule
import com.cristianprincipi.habits.data.sync.WorkManagerSyncScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * M14.10 — the real activity asks the real WorkManager for an app-open sync, and a rotation does not.
 *
 * `AppOpenSyncTest` proves the rule on the JVM. What it cannot prove is the assumption the rule rests
 * on: that `isChangingConfigurations` is already true when `onSaveInstanceState` runs on the way out
 * to a recreation. If it were not, every rotation would sync and no JVM test would notice.
 *
 * ### How a request is seen without a fake
 *
 * The request is `KEEP` under one unique name. While a run is unfinished, a second request changes
 * nothing, so "asked again" and "not asked" would look the same. Each check therefore waits for the
 * run to **finish** first; after that, a new request enqueues work with a **new id**. The app on this
 * device has no Notion connection (`FreshInstallRule`), so a run ends at once without a request.
 *
 * ⚠ It needs the emulator to have a network: the work carries a `CONNECTED` constraint.
 */
@RunWith(AndroidJUnit4::class)
class AppOpenSyncDeviceTest {
    @get:Rule
    val freshInstall = FreshInstallRule()

    private val workManager = WorkManager.getInstance(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun openingSyncsARotationDoesNotAndComingBackDoes() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val afterLaunch = awaitFinishedRun(seen = emptySet())

            scenario.recreate()
            SystemClock.sleep(SETTLE_MILLIS)
            assertEquals("a rotation must not ask for a sync", afterLaunch, appOpenWorkIds())

            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            val afterReturn = awaitFinishedRun(seen = afterLaunch)
            assertTrue("coming back must ask again", (afterReturn - afterLaunch).isNotEmpty())
        }
    }

    /** Waits for an app-open run with an id not in [seen] to finish, and returns every id. */
    private fun awaitFinishedRun(seen: Set<UUID>): Set<UUID> {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            val infos = workManager.getWorkInfosForUniqueWork(WorkManagerSyncScheduler.APP_OPEN_WORK).get()
            val fresh = infos.filter { it.id !in seen }
            if (fresh.isNotEmpty() && fresh.all { it.state.isFinished }) return infos.map { it.id }.toSet()
            SystemClock.sleep(POLL_MILLIS)
        }
        error("no new app-open sync finished within ${TIMEOUT_MILLIS}ms: ${appOpenWorkIds()} (seen $seen)")
    }

    private fun appOpenWorkIds(): Set<UUID> =
        workManager
            .getWorkInfosForUniqueWork(WorkManagerSyncScheduler.APP_OPEN_WORK)
            .get()
            .map { it.id }
            .toSet()

    private companion object {
        const val TIMEOUT_MILLIS = 20_000L
        const val POLL_MILLIS = 200L

        /** Long enough for a request made in `onStart` to reach WorkManager's database. */
        const val SETTLE_MILLIS = 3_000L
    }
}
