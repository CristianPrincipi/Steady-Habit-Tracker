package com.cristianprincipi.habits.core.testing

import com.cristianprincipi.habits.core.widget.WidgetRefresher

/**
 * A [WidgetRefresher] that counts instead of touching Glance.
 *
 * ⭐ **Shared rather than duplicated** for the reason `FakeNotionSetupRepository` is: three places
 * build a real `HabitRepositoryImpl` — one JVM test and two instrumented ones — and a second copy
 * of a fake is how two suites start disagreeing about what a check-off does.
 *
 * ⚠ **[failWith] exists because the interesting assertion is that a failure changes nothing.**
 * `WidgetRefresher.refresh` promises never to throw, and a check-off is `CLAUDE.md` §1's promise
 * that a user action always succeeds locally — so a test that only ever refreshes successfully
 * cannot tell whether the second promise depends on the first.
 */
class RecordingWidgetRefresher(
    private val failWith: Throwable? = null,
) : WidgetRefresher {
    var refreshes: Int = 0
        private set

    override suspend fun refresh() {
        refreshes++
        failWith?.let { throw it }
    }
}
