package com.cristianprincipi.habits.core.ui

/**
 * Which starts of the app's activity are *the app coming to the front* — M14.10.
 *
 * The user's ruling (2026-09-13): sync **every time the app comes to the front**, not only on a fresh
 * launch — so an edit made in Notion on a computer is there when they look. For a single-activity
 * app that is almost exactly `onStart`, and the "almost" is this class.
 *
 * ### ⚠ The one start that is not an opening
 *
 * A rotation, a dark-mode switch or a font-size change **recreates** the activity, and the new
 * instance goes through `onStart` like any other. Nobody opened anything, so it must not sync. The
 * activity learns which kind of start is coming from its saved state: `isChangingConfigurations`,
 * written in `onSaveInstanceState`, is true only on the way out to a recreation.
 *
 * Everything else *is* an opening, and deliberately so:
 * - **a fresh launch**, from the launcher or a widget;
 * - **a return from the background**, including from Notion itself after following a link out;
 * - **a restore after process death** — the saved state then says "not a configuration change",
 *   because it was written when the user left, and a user coming back to a killed app has been away
 *   longest of all.
 *
 * ⭐ A plain class rather than code in the activity, because the rule is the part worth testing and
 * this project's instrumented tests have no Hilt test runner to swap the repository behind an
 * activity (`AppOpenSyncTest` runs on the JVM instead).
 */
class AppOpenSync(
    private val requestSync: () -> Unit,
) {
    private var nextStartIsARecreation = false

    /** @param recreatedForConfigurationChange what the previous instance saved, or false if none. */
    fun onCreate(recreatedForConfigurationChange: Boolean) {
        nextStartIsARecreation = recreatedForConfigurationChange
    }

    fun onStart() {
        if (nextStartIsARecreation) {
            // Only the first start after the recreation. The next one is the user coming back.
            nextStartIsARecreation = false
            return
        }
        requestSync()
    }
}
