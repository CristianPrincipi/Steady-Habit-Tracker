package com.cristianprincipi.habits.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.designsystem.component.EmptyState
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme

/**
 * No habits at all — **not "nothing this week"**, which cannot happen (see [HabitsEmptyReason]).
 *
 * ⚠ **The copy is UNDESIGNED** (M2.8); the user judged it at M6.11 on the Week screen, and it says
 * the same thing here. What is settled is the *shape*: two sentences, and no "add a habit" button
 * in either — ADR-010 removed habit creation, so there is no `+` this app could ever honour.
 *
 * Two sentences rather than one, for ADR-072's reason: the setup wizard saves the mapping — which
 * flips the shell's gate — *before* it awaits the first sync, so a wizard holding an error is
 * already off screen by the time the user lands here. This is the only surface left to say so.
 *
 * ### Why the failed case gets a button and the pending one does not
 *
 * A held or running sync asks nothing of the user, and offering a control for it would be the
 * nagging `CLAUDE.md` §1 rules out by treating offline as normal rather than as an error. A failed
 * one is the opposite: it is the **only** notice the user will get, because the wizard that would
 * have reported a failed first import is off screen by the time the failure arrives (ADR-072).
 *
 * It goes *to* sync status rather than retrying here. That screen already owns the sentence for
 * every `SyncFailure` and the "Sync now" button `CLAUDE.md` §3.5 asks for, and a retry on this
 * screen would be a second control to keep in step with `SyncStatusUiState.canRetry` — which is
 * disabled exactly when a run is already owed (ADR-060).
 */
@Composable
fun HabitsEmpty(
    reason: HabitsEmptyReason,
    onOpenSyncStatus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // ⚠ Centred, and `EmptyState` is left to size itself. Handing it `fillMaxSize` is what M6.8's
    // first build did, and on a real screen — rather than the 400dp preview — that pins three lines
    // to the very top with most of a phone of black under them, which reads as a load that stopped
    // halfway rather than as a finished screen. Seen on the emulator, fixed in the same turn
    // (`CLAUDE.md` §7).
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (reason) {
            HabitsEmptyReason.SyncPending -> {
                EmptyState(
                    // A ticked box, because that is literally what a habit is upstream: a checkbox
                    // property on a Notion page (`docs/notion-schema.md`).
                    emoji = stringResource(R.string.habits_empty_pending_emoji),
                    title = stringResource(R.string.habits_empty_pending_title),
                    body = stringResource(R.string.habits_empty_pending_body),
                )
            }

            HabitsEmptyReason.SyncFailed -> {
                EmptyState(
                    emoji = stringResource(R.string.habits_empty_failed_emoji),
                    title = stringResource(R.string.habits_empty_failed_title),
                    body = stringResource(R.string.habits_empty_failed_body),
                    actionLabel = stringResource(R.string.habits_empty_failed_action),
                    onAction = onOpenSyncStatus,
                )
            }
        }
    }
}

@Preview(name = "Empty — first import on its way", backgroundColor = 0xFF111113, showBackground = true, widthDp = 360)
@Composable
private fun HabitsEmptyPendingPreview() {
    HabitsTheme {
        HabitsEmpty(reason = HabitsEmptyReason.SyncPending, onOpenSyncStatus = {})
    }
}

@Preview(name = "Empty — first import failed", backgroundColor = 0xFF111113, showBackground = true, widthDp = 360)
@Composable
private fun HabitsEmptyFailedPreview() {
    HabitsTheme {
        HabitsEmpty(reason = HabitsEmptyReason.SyncFailed, onOpenSyncStatus = {})
    }
}
