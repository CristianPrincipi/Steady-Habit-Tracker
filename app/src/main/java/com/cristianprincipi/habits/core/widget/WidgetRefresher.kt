package com.cristianprincipi.habits.core.widget

import kotlinx.coroutines.CancellationException

/**
 * "Something a home-screen widget shows has changed — go and look again."
 *
 * ⭐ **A port, so the data layer can say this without knowing what a widget is.** The only
 * implementation lives in `feature/widget/` and talks to Glance; `HabitRepositoryImpl` and
 * `SyncWorker` depend on this interface, which keeps `data → feature` from becoming a real edge.
 * It sits in `core/` for the reason `core/network/`'s `ConnectivityObserver` does: it is a platform
 * surface the data layer legitimately needs to reach, and `domain/` holds repositories and use
 * cases rather than ports onto Android.
 *
 * ⚠⚠ **This exists BESIDE the widget's own live read, not instead of it, and both halves are
 * needed** (ADR-130). A widget with an open Glance session re-reads Room by itself; this is what
 * wakes one that has no session, which is most of the time. Neither alone is correct: without the
 * live read a second check-off inside 45 seconds changes nothing, and without this a check-off with
 * no session running changes nothing at all.
 */
fun interface WidgetRefresher {
    suspend fun refresh()
}

/**
 * [refresh], with the guarantee that matters: **a home-screen widget can never fail a check-off.**
 *
 * ⚠⚠ A check-off is `CLAUDE.md` §1's promise that a user action always succeeds locally, and M13.12
 * made every ViewModel write surface its failures to the user — so an exception escaping a widget
 * repaint would put a red message under a tap that worked perfectly. The same reasoning covers
 * `SyncWorker`, where it would become a WorkManager retry of a sync that had already succeeded.
 *
 * ⭐ **Here rather than inside the implementation, deliberately.** Swallowing in
 * `HabitWidgetRefresher` would make the guarantee a property of one class that a second
 * implementation could quietly drop; as an extension it holds for every implementation, including
 * the fakes, and it is the call sites that carry the risk.
 *
 * `CancellationException` is re-thrown so a cancelled sync or a cancelled `viewModelScope` still
 * unwinds properly — swallowing it would break structured concurrency to protect against nothing.
 */
suspend fun WidgetRefresher.refreshQuietly() {
    try {
        refresh()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (
        @Suppress("TooGenericExceptionCaught", "SwallowedException") throwable: Throwable,
    ) {
        // Deliberately silent, and the KDoc above is the reason.
    }
}
