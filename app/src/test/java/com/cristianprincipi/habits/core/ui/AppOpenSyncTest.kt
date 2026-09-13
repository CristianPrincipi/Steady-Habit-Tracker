package com.cristianprincipi.habits.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * M14.10 — which activity starts sync. One test per kind of start, because each is a case the user
 * will produce without thinking about it.
 */
class AppOpenSyncTest {
    private var requests = 0
    private val appOpenSync = AppOpenSync { requests++ }

    @Test
    fun aFreshLaunchSyncs() {
        appOpenSync.onCreate(recreatedForConfigurationChange = false)
        appOpenSync.onStart()

        assertEquals(1, requests)
    }

    @Test
    fun comingBackFromTheBackgroundSyncsEveryTime() {
        // The user's ruling: every time the app comes to the front, not only on a fresh launch.
        appOpenSync.onCreate(recreatedForConfigurationChange = false)
        appOpenSync.onStart()
        appOpenSync.onStart()
        appOpenSync.onStart()

        assertEquals(3, requests)
    }

    @Test
    fun aRotationDoesNotSync() {
        // The recreated instance starts again with nobody having opened anything.
        appOpenSync.onCreate(recreatedForConfigurationChange = true)
        appOpenSync.onStart()

        assertEquals(0, requests)
    }

    @Test
    fun theFirstReturnAfterARotationStillSyncs() {
        // Skipping is for the recreation's own start only. Leaving the app afterwards and coming
        // back is an opening like any other.
        appOpenSync.onCreate(recreatedForConfigurationChange = true)
        appOpenSync.onStart()
        appOpenSync.onStart()

        assertEquals(1, requests)
    }

    @Test
    fun aRestoreAfterProcessDeathSyncs() {
        // The saved state was written when the user left, which was not a configuration change.
        // Of every way back into the app, this is the one after the longest absence.
        appOpenSync.onCreate(recreatedForConfigurationChange = false)
        appOpenSync.onStart()

        assertEquals(1, requests)
    }
}
