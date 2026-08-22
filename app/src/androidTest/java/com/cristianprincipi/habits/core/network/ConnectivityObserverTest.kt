package com.cristianprincipi.habits.core.network

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * That the observer emits **at all**, promptly, whatever the device's network is doing.
 *
 * **Read this before trusting a green run.** These tests pass on an ONLINE device even if the
 * seeding emission is deleted, because `registerDefaultNetworkCallback` replays the current default
 * network to a newly registered callback. They only bite when the device is **offline** — there is
 * no default network then, so nothing fires at all.
 *
 * Both directions were probed on a real emulator on 2026-08-22 (ADR-041): with airplane mode on and
 * the seeding removed, all three of these fail; with it restored, all three pass. Reproduce it with
 * `adb shell cmd connectivity airplane-mode enable`.
 *
 * That asymmetry is why the suite is not left to prove it: **a green run here is not evidence the
 * offline path works**, only that the online one does.
 *
 * **Nothing asserts `true` or `false`.** Whether this machine has validated internet right now is an
 * environment fact, not a property of the code, and a test that fails on a plane is a test people
 * learn to ignore. What matters is that a value arrives.
 */
@RunWith(AndroidJUnit4::class)
class ConnectivityObserverTest {
    private val observer =
        AndroidConnectivityObserver(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun itEmitsTheCurrentStateWithoutBeingProdded() =
        runBlocking {
            val first = withTimeoutOrNull(TIMEOUT_MILLIS) { observer.isOnline.first() }

            assertNotNull(
                "isOnline emitted nothing in ${TIMEOUT_MILLIS}ms. If this device is OFFLINE, the " +
                    "seeding emission before registerDefaultNetworkCallback has probably been lost.",
                first,
            )
        }

    @Test
    fun itCanBeCollectedMoreThanOnce() =
        runBlocking {
            // Each collector registers its own callback and must unregister it on cancellation.
            // A leaked registration would eventually throw TooManyRequestsException from
            // ConnectivityManager, which caps concurrent callbacks per process.
            repeat(REPEATS) {
                val value = withTimeoutOrNull(TIMEOUT_MILLIS) { observer.isOnline.first() }
                assertNotNull("Collection $it emitted nothing", value)
            }
        }

    @Test
    fun itDoesNotRepeatAValueThatHasNotChanged() =
        runBlocking {
            // distinctUntilChanged: the callbacks fire in bursts during a handover — onAvailable
            // and onCapabilitiesChanged both land — and a Flow that re-emitted the same boolean
            // would restart whatever the sync engine hangs off it.
            val values =
                withTimeoutOrNull(TIMEOUT_MILLIS) {
                    observer.isOnline.take(1).toList()
                }

            assertEquals(1, values?.size)
        }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
        const val REPEATS = 3
    }
}
