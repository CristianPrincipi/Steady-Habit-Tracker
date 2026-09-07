package com.cristianprincipi.habits.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Runs a write to local storage, and **reports the failure instead of taking the app down**.
 *
 * ### Why this exists — M13.12, raised by M12.7's sweep (ADR-104 §5)
 *
 * Every write in this app was a bare `viewModelScope.launch { … }`. `viewModelScope` is built on a
 * `SupervisorJob`, which stops one failed child cancelling its siblings — it does **not** stop the
 * exception. With no [kotlinx.coroutines.CoroutineExceptionHandler] installed anywhere (there is
 * none in this app: checked, not assumed) it reaches the thread's uncaught handler, and the app
 * dies. The write that matters most is the **check-off**, which `CLAUDE.md` §1 promises "succeeds
 * immediately and locally… never lost".
 *
 * ### ⚠⚠ Why the fix could not be a bare `try`/`catch`
 *
 * Catching and moving on converts a crash into the **silent no-op** M12.7 forbids — and on the
 * Week screen that is worse than the crash. The pills are drawn from Room, observed, with no
 * optimistic overlay: if the write does not land, the state never re-emits, and the pill **simply
 * does not move**. The user sees exactly what a mis-tap looks like, on the one interaction the app
 * exists for. So [onFailure] is the point of this function, and the default is only for the callers
 * that have argued for it in writing.
 *
 * ### ⚠ `CancellationException` is rethrown, and it must be
 *
 * It arrives here as an ordinary [Exception] — `kotlinx.coroutines.CancellationException` is a
 * `java.util.concurrent.CancellationException`, which is an `IllegalStateException`. Swallowing it
 * would break structured concurrency, and it would do so *visibly*: leaving the tab cancels
 * `viewModelScope`, and a caller would report "couldn't save" for a write the user had simply
 * navigated away from.
 *
 * ### Why [onFailure] is given nothing
 *
 * There is nowhere for a cause to go. This app has **no telemetry** (`CLAUDE.md` §8) and no crash
 * reporter, so a `Throwable` parameter could only be logged — and `CLAUDE.md` §3.6 forbids logging
 * anything that might carry the Notion token, which the auth DataStore's exceptions sit closest to.
 * What the user needs is the sentence and the retry, and neither is derived from the cause.
 *
 * ### Why the catch is as broad as it is
 *
 * Room throws `SQLiteException` and DataStore throws `IOException`, and the honest set is "whatever
 * the device did instead of writing" — a disk that is full, a file that cannot be opened, storage
 * unmounted mid-write. Narrowing it would leave behind the uncaught branch this function exists to
 * remove, so the breadth is the feature rather than a shortcut. `TooGenericExceptionCaught` and
 * `SwallowedException` are suppressed for exactly that, and the exception is unused on purpose.
 *
 * @param onFailure what to say. The default says nothing, and every caller that takes it has a
 *   written reason beside it.
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
fun ViewModel.launchWrite(
    onFailure: () -> Unit = {},
    block: suspend () -> Unit,
): Job =
    viewModelScope.launch {
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (couldNotWrite: Exception) {
            onFailure()
        }
    }
