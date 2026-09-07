package com.cristianprincipi.habits.feature.week

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.datetime.shortDateLabel
import com.cristianprincipi.habits.core.ui.rememberCurrentLocale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/**
 * The landing tab, and the app's whole value proposition — `docs/design-spec.md` §3.
 *
 * Stateful half only: it collects the state, forwards events, and hosts the undo snackbar.
 * Everything drawn is in [WeekContent], which is the split `CLAUDE.md` §6 asks for and the reason
 * that file's previews can exist at all.
 *
 * ### The snackbar is hosted here, not in the shell — ADR-070
 *
 * `HabitsShell` owns a `Scaffold` with a `snackbarHost` slot going spare, so the shell looks like
 * the obvious home. It is not, for four reasons:
 *
 * - **The message belongs to one screen.** `CLAUDE.md` §6 gives every screen its own one-shot
 *   channel. A shell-level host would need a `SnackbarHostState` threaded through every
 *   `composable<>` entry, or a `CompositionLocal` reaching down past each screen root — the
 *   coupling §6 exists to prevent, paid for a message nothing else sends.
 * - **The pixels are the same either way.** The shell pads its `NavHost` by the bottom bar's
 *   height (ADR-063), so the bottom of this screen *is* the top of the bar. A snackbar aligned to
 *   the bottom here lands where `Scaffold` would have put it.
 * - **The lifetime is right.** Navigate away and the offer goes with the screen. A shell-hosted
 *   snackbar would outlive the pill its Undo acts on, and stay up over a tab that has nothing to do
 *   with it.
 * - **Nothing has to be un-shared later.** When sync errors want a snackbar, that screen gets its
 *   own, with its own copy and its own channel.
 */
@Composable
fun WeekScreen(
    // Only reachable from the empty state, and only when the first import failed — see [WeekEmpty].
    // The landing tab needs a way through to sync status because that failure has nowhere else to
    // be reported: the wizard that would have shown it is off screen by then (ADR-072).
    onOpenSyncStatus: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WeekViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    WeekWithUndo(
        state = state,
        messages = viewModel.messages,
        onEvent = viewModel::onEvent,
        onOpenSyncStatus = onOpenSyncStatus,
        modifier = modifier,
    )
}

/**
 * [WeekContent] plus the snackbar over it — [WeekScreen] with the ViewModel taken out.
 *
 * ⭐ **This seam exists so the snackbar can be looked at.** `WeekScreen` needs a Hilt graph and a
 * Notion connection to show anything, and connecting costs the **user** a paste of a token only they
 * have (`CLAUDE.md` §7). `WeekPreviewActivity` plugs its own `Flow` in here instead, which means
 * what gets watched on the emulator is the composition that ships rather than a debug re-creation
 * of it — the difference that makes M6.11's judgement worth anything.
 *
 * A `Box` rather than a nested `Scaffold`: the only slot wanted is the snackbar host, and a second
 * `Scaffold` inside the shell's would take the window insets twice (ADR-063 puts them at the shell).
 */
@Composable
internal fun WeekWithUndo(
    state: WeekUiState,
    messages: Flow<WeekMessage>,
    onEvent: (WeekEvent) -> Unit,
    onOpenSyncStatus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    UndoSnackbars(messages = messages, hostState = snackbarHostState, onEvent = onEvent)

    Box(modifier = modifier.fillMaxSize()) {
        WeekContent(
            state = state,
            onEvent = onEvent,
            onOpenSyncStatus = onOpenSyncStatus,
            modifier = Modifier.fillMaxSize(),
        )
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * Turns each [WeekMessage] into one snackbar, and its action into one event.
 *
 * **Two shapes since M13.12**: a write that landed offers **Undo**, a write that did not offers
 * **Retry**. They differ in more than copy — see the `duration` on each.
 *
 * ### ⚠ `collectLatest`, so the newest tap wins immediately
 *
 * `showSnackbar` suspends until its snackbar goes away, so a plain `collect` would show them
 * strictly one after another: check off five habits and the fifth sentence appears twenty seconds
 * after the fifth tap, describing something the user has long since stopped thinking about.
 * `collectLatest` cancels the one on screen when a new message arrives, and a cancelled
 * `showSnackbar` dismisses. Together with the `CONFLATED` channel behind [WeekViewModel.messages],
 * a fast hand produces one snackbar that keeps being replaced rather than a queue that keeps
 * growing.
 *
 * The strings are resolved out here, in composition, rather than in the ViewModel — `CLAUDE.md` §6
 * keeps `android.*` out of one and every user-facing string in `strings.xml`.
 *
 * It takes the `Flow`, not the ViewModel: §6 allows no `ViewModel` reference below the screen root,
 * and this is below it even though it sits in the same file.
 */
@Composable
private fun UndoSnackbars(
    messages: Flow<WeekMessage>,
    hostState: SnackbarHostState,
    onEvent: (WeekEvent) -> Unit,
) {
    val locale = rememberCurrentLocale()
    val checked = stringResource(R.string.week_undo_checked)
    val cleared = stringResource(R.string.week_undo_cleared)
    val undo = stringResource(R.string.week_undo_action)
    val failedChecked = stringResource(R.string.week_save_failed_checked)
    val failedCleared = stringResource(R.string.week_save_failed_cleared)
    val failedUnnamed = stringResource(R.string.week_save_failed_unnamed)
    val retry = stringResource(R.string.week_save_failed_action)

    LaunchedEffect(messages, hostState, locale) {
        messages.collectLatest { message ->
            val day = shortDateLabel(message.date, locale)
            val result =
                when (message) {
                    is WeekMessage.Toggled -> {
                        hostState.showSnackbar(
                            // Always a date, never "today": M6.11 made a *successful* tap on today
                            // silent, so no `Toggled` that reaches here describes it.
                            message = (if (message.completed) checked else cleared).format(message.habitName, day),
                            actionLabel = undo,
                            // Short, because the pill has already shown the change and this is a
                            // second chance rather than the news itself. Long would park it over the
                            // bottom card for most of the time the user spends checking the rest of
                            // the week off.
                            duration = SnackbarDuration.Short,
                        )
                    }

                    is WeekMessage.SaveFailed -> {
                        hostState.showSnackbar(
                            // ⚠ The unnamed sentence names no day either — a message that cannot say
                            // *which* habit has nothing useful to add by saying which day, and the
                            // one thing this case may never do is say nothing at all.
                            message =
                                message.habitName?.let { name ->
                                    (if (message.completed) failedChecked else failedCleared).format(name, day)
                                } ?: failedUnnamed,
                            actionLabel = retry,
                            // ⚠⚠ **Long, unlike the success case, and not for symmetry's sake.** The
                            // pill did not move, so this snackbar is the *only* thing on screen
                            // saying anything happened at all — and Retry is the fix, so it has to
                            // outlast a glance. `Short` would let the app's core promise fail
                            // quietly in four seconds.
                            duration = SnackbarDuration.Long,
                        )
                    }
                }
            if (result == SnackbarResult.ActionPerformed) {
                onEvent(
                    when (message) {
                        is WeekMessage.Toggled -> message.undo
                        is WeekMessage.SaveFailed -> message.retry
                    },
                )
            }
        }
    }
}
