package com.cristianprincipi.habits.feature.syncstatus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.datetime.Elapsed
import com.cristianprincipi.habits.core.designsystem.component.DestructiveButton
import com.cristianprincipi.habits.core.designsystem.component.HabitsCard
import com.cristianprincipi.habits.core.designsystem.component.NotionFailureBanner
import com.cristianprincipi.habits.core.designsystem.component.SecondaryButton
import com.cristianprincipi.habits.core.designsystem.component.SectionEyebrow
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Sizing
import com.cristianprincipi.habits.core.designsystem.theme.Spacing
import com.cristianprincipi.habits.domain.model.DiscardedEditNotice
import com.cristianprincipi.habits.domain.model.EditSide
import com.cristianprincipi.habits.domain.model.SyncFailure
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Sync status, retry, the notices, and disconnect.
 *
 * Stateful half only — see [SyncStatusContent] for everything drawn (`CLAUDE.md` §6).
 */
@Composable
fun SyncStatusScreen(
    modifier: Modifier = Modifier,
    viewModel: SyncStatusViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SyncStatusContent(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

/**
 * Everything the screen draws.
 *
 * **Nothing here blocks.** `CLAUDE.md` §3.5 wants a failure to be "a clear, non-blocking,
 * human-readable message with a retry affordance", so a failure is a banner in the flow of the page
 * with a button under it — never a dialog. The one dialog on this screen is the disconnect
 * confirmation, which is a destructive action the user asked for.
 */
@Composable
fun SyncStatusContent(
    state: SyncStatusUiState,
    onEvent: (SyncStatusEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.settingsSectionGap),
    ) {
        Text(
            text = stringResource(R.string.sync_title),
            style = MaterialTheme.typography.headlineSmall,
            color = HabitsTheme.colors.text.primary,
        )

        ConnectionCard(state = state)

        state.failure?.let { failure -> NotionFailureBanner(failure = failure) }

        // Shown for the whole of a connection and only ever disabled, so that neither a held sync
        // nor a running one can move the destructive button below it. ADR-060.
        if (state.isConnected) {
            SecondaryButton(
                text = stringResource(R.string.sync_retry),
                onClick = { onEvent(SyncStatusEvent.RetryPressed) },
                enabled = state.canRetry,
                isBusy = state.isSyncing,
            )
        }

        if (state.hasDiscardedEdits) {
            DiscardedEditsCard(notices = state.discardedEdits, onEvent = onEvent)
        }

        if (state.isConnected) {
            DestructiveButton(
                text = stringResource(R.string.sync_disconnect),
                onClick = { onEvent(SyncStatusEvent.DisconnectPressed) },
            )
        }
    }

    if (state.isConfirmingDisconnect) {
        DisconnectDialog(willDeleteLocalData = state.willDeleteLocalData, onEvent = onEvent)
    }
}

/**
 * The status card the prototype does design (`docs/design-spec.md` §6.1): a small dot beside two
 * lines of text.
 *
 * Its two states were "Connected / Live sync enabled" and "Not connected / Showing demo data". The
 * first line is kept; the second is replaced, because this app has no demo data and because the
 * useful thing to say under "Connected" is **when the data was last actually correct**.
 */
@Composable
private fun ConnectionCard(
    state: SyncStatusUiState,
    modifier: Modifier = Modifier,
) {
    HabitsCard(modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(Sizing.statusDot)
                        .background(
                            color =
                                if (state.isConnected) {
                                    HabitsTheme.colors.success
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                            shape = CircleShape,
                        ),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.yearDotGap)) {
                Text(
                    text =
                        stringResource(
                            if (state.isConnected) R.string.sync_connected else R.string.sync_not_connected,
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = HabitsTheme.colors.text.primary,
                )
                Text(
                    text = state.secondLine(),
                    style = MaterialTheme.typography.bodySmall,
                    color = HabitsTheme.colors.text.secondary,
                )
            }
        }
    }
}

/**
 * "Last synced 3 minutes ago", or what stands in for it.
 *
 * **A sync in progress wins over the timestamp**, because it is the more current fact and the one
 * that explains why the retry button has gone. ⭐ A sync that is *waiting* wins for the same reason
 * (M4.16) and sits directly below it: it is likewise the current fact, it likewise explains the
 * missing button, and it is the only line here that answers a tap the user has already made.
 */
@Composable
private fun SyncStatusUiState.secondLine(): String =
    when {
        !isConnected -> stringResource(R.string.sync_not_connected_body)
        isSyncing -> stringResource(R.string.sync_in_progress)
        isWaitingForNetwork -> stringResource(R.string.sync_waiting_for_network)
        lastSynced == null -> stringResource(R.string.sync_never)
        else -> lastSynced.describe()
    }

@Composable
private fun Elapsed.describe(): String =
    when (this) {
        Elapsed.JustNow -> stringResource(R.string.sync_just_now)
        is Elapsed.Minutes -> pluralStringResource(R.plurals.sync_minutes_ago, count.toInt(), count)
        is Elapsed.Hours -> pluralStringResource(R.plurals.sync_hours_ago, count.toInt(), count)
        is Elapsed.Days -> pluralStringResource(R.plurals.sync_days_ago, count.toInt(), count)
    }

/**
 * ⭐ **The half of `CLAUDE.md` §3.5 that M4.9 could not deliver on its own.**
 *
 * That rule permits last-write-wins to discard a user's edit and forbids doing it silently. The
 * engine records what it destroyed; this is where the app admits to it. Dismissing clears them all —
 * they are an apology, not a task list.
 */
@Composable
private fun DiscardedEditsCard(
    notices: List<DiscardedEditNotice>,
    onEvent: (SyncStatusEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    HabitsCard(modifier = modifier.fillMaxWidth()) {
        SectionEyebrow(text = stringResource(R.string.sync_discarded_eyebrow))
        Text(
            text = stringResource(R.string.sync_discarded_body),
            style = MaterialTheme.typography.bodySmall,
            color = HabitsTheme.colors.text.secondary,
        )
        notices.forEach { notice ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.yearDotGap)) {
                Text(
                    text =
                        stringResource(
                            when (notice.discardedSide) {
                                EditSide.Local -> R.string.sync_discarded_local
                                EditSide.Remote -> R.string.sync_discarded_remote
                            },
                            notice.date.format(dayFormatter),
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = HabitsTheme.colors.text.primary,
                )
                Text(
                    // The names when they can still be found, and the count when they cannot: a
                    // habit whose Notion column was deleted has no name left, and "1 habit
                    // affected" is still true and still worth saying.
                    text =
                        notice.habitNames.takeIf(List<String>::isNotEmpty)?.joinToString()
                            ?: pluralStringResource(
                                R.plurals.sync_discarded_habits,
                                notice.habitCount,
                                notice.habitCount,
                            ),
                    style = MaterialTheme.typography.bodySmall,
                    color = HabitsTheme.colors.text.secondary,
                )
            }
        }
        TextButton(onClick = { onEvent(SyncStatusEvent.NoticesDismissed) }) {
            Text(
                text = stringResource(R.string.sync_discarded_dismiss),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * The one dialog on this screen, and the one place a modal is right: an action that removes things.
 *
 * **The warning about deleting local data appears only when the box is ticked.** Showing it always
 * would make disconnecting look destructive when it is not — nothing in Notion is touched, and the
 * history survives unless the user says otherwise.
 */
@Composable
private fun DisconnectDialog(
    willDeleteLocalData: Boolean,
    onEvent: (SyncStatusEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = { onEvent(SyncStatusEvent.DisconnectCancelled) },
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = {
            Text(
                text = stringResource(R.string.sync_disconnect_title),
                style = MaterialTheme.typography.titleMedium,
                color = HabitsTheme.colors.text.primary,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
                Text(
                    text = stringResource(R.string.sync_disconnect_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = HabitsTheme.colors.text.secondary,
                )
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = willDeleteLocalData,
                                role = Role.Checkbox,
                                onValueChange = { onEvent(SyncStatusEvent.DeleteLocalDataToggled(it)) },
                            ),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = willDeleteLocalData, onCheckedChange = null)
                    Text(
                        text = stringResource(R.string.sync_disconnect_delete_local),
                        style = MaterialTheme.typography.bodyMedium,
                        color = HabitsTheme.colors.text.primary,
                    )
                }
                if (willDeleteLocalData) {
                    Text(
                        text = stringResource(R.string.sync_disconnect_delete_local_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onEvent(SyncStatusEvent.DisconnectConfirmed) }) {
                Text(
                    text = stringResource(R.string.sync_disconnect_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(SyncStatusEvent.DisconnectCancelled) }) {
                Text(
                    text = stringResource(R.string.sync_disconnect_cancel),
                    color = HabitsTheme.colors.text.secondary,
                )
            }
        },
    )
}

/** The device's own medium date format, so a notice reads the way the user's other apps do. */
private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 400, heightDp = 800)
@Composable
private fun SyncStatusHealthyPreview() {
    HabitsTheme {
        SyncStatusContent(
            state =
                SyncStatusUiState(
                    isConnected = true,
                    lastSynced = Elapsed.Minutes(3),
                ),
            onEvent = {},
        )
    }
}

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 400, heightDp = 800)
@Composable
private fun SyncStatusFailedPreview() {
    val previewNotices =
        listOf(
            DiscardedEditNotice(
                date = LocalDate.of(2026, 8, 21),
                habitNames = listOf("📖 Read", "✒️ Journal"),
                habitCount = 2,
                discardedSide = EditSide.Local,
                resolvedAt = Instant.parse("2026-08-23T09:00:00Z"),
            ),
            // A habit whose Notion column has since been deleted: no name left, count still true.
            DiscardedEditNotice(
                date = LocalDate.of(2026, 8, 19),
                habitNames = emptyList(),
                habitCount = 1,
                discardedSide = EditSide.Remote,
                resolvedAt = Instant.parse("2026-08-22T18:30:00Z"),
            ),
        )
    HabitsTheme {
        SyncStatusContent(
            state =
                SyncStatusUiState(
                    isConnected = true,
                    lastSynced = Elapsed.Days(2),
                    failure = SyncFailure.TokenRejected,
                    discardedEdits = previewNotices,
                ),
            onEvent = {},
        )
    }
}

/**
 * ⭐ M4.16: tapped "Sync now" with no network.
 *
 * The whole defect in one frame — the card answers, and the button it answers for is gone.
 */
@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 400, heightDp = 800)
@Composable
private fun SyncStatusWaitingForNetworkPreview() {
    HabitsTheme {
        SyncStatusContent(
            state =
                SyncStatusUiState(
                    isConnected = true,
                    isWaitingForNetwork = true,
                    lastSynced = Elapsed.Hours(2),
                ),
            onEvent = {},
        )
    }
}

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 400, heightDp = 800)
@Composable
private fun SyncStatusDisconnectPreview() {
    HabitsTheme {
        SyncStatusContent(
            state =
                SyncStatusUiState(
                    isConnected = true,
                    lastSynced = Elapsed.JustNow,
                    isConfirmingDisconnect = true,
                    willDeleteLocalData = true,
                ),
            onEvent = {},
        )
    }
}
