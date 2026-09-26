package com.cristianprincipi.habits.core.widget

import kotlinx.coroutines.CancellationException

/**
 * "Hand the system a fresh picture of each widget, for the picker to show" — M15.9.
 *
 * ⭐ **A second port beside [WidgetRefresher] rather than a second method on it**, and the reason is
 * that they are not the same event. A refresh means *what a placed widget shows has changed*, and it
 * fires on every check-off, every colour change and every sync that pulled a day. A preview push is
 * a once-per-launch errand that touches no placed widget at all — and the platform **rate-limits**
 * it, so a port that invited a caller to do both at once would be one refactor away from burning
 * that limit on every tick of a habit.
 *
 * ⚠ `WidgetRefresher` is a `fun interface` and could not have grown a second method anyway without
 * ceasing to be one.
 *
 * ⚠⚠ **Below API 35 this does nothing, and nothing is the correct answer.** The platform call behind
 * it — `AppWidgetManager.setWidgetPreview`, reached through Glance's own `AppWidgetManagerApi35Impl`
 * — does not exist before then. Those devices fall back to the picker's app-icon path, which is why
 * both receivers now state `android:icon` explicitly instead of inheriting it.
 */
fun interface WidgetPreviewPublisher {
    suspend fun publish()
}

/**
 * [publish], with the guarantee that matters: **a picker preview can never fail a cold start.**
 *
 * ⚠⚠ This runs from `MainActivity.onCreate`, on the path the user is waiting behind. A preview is
 * decoration on a screen they are not looking at; an exception escaping it would be a crash on
 * launch caused by a picture nobody had asked to see.
 *
 * ⭐ An extension rather than a `try` inside the implementation, exactly as [refreshQuietly] is: the
 * guarantee then holds for every implementation including the fakes, and it is the call site that
 * carries the risk. `CancellationException` is re-thrown so a cancelled `lifecycleScope` still
 * unwinds properly.
 */
suspend fun WidgetPreviewPublisher.publishQuietly() {
    try {
        publish()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (
        @Suppress("TooGenericExceptionCaught", "SwallowedException") throwable: Throwable,
    ) {
        // Deliberately silent, and the KDoc above is the reason.
    }
}
