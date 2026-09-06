package com.cristianprincipi.habits.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.datetime.Elapsed
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Sizing
import com.cristianprincipi.habits.core.designsystem.theme.Spacing

/**
 * The status card the prototype does design (`docs/design-spec.md` §6.1): a small dot beside two
 * lines of text.
 *
 * Its two states were "Connected / Live sync enabled" and "Not connected / Showing demo data". The
 * first line is kept; the second is replaced, because this app has no demo data and because the
 * useful thing to say under "Connected" is **when the data was last actually correct**.
 *
 * ⭐ **In the design system rather than in a feature, and that is M11.1's doing.** It lived private
 * inside `SyncStatusScreen` from M4.12 until Settings became a hub with the same card at the top of
 * it. Two spellings of "are we connected, and when did it last work" would be two chances to drift
 * — and the drift would be invisible, because each screen's own test would still pass.
 */
@Composable
fun ConnectionStatusCard(
    summary: ConnectionSummary,
    modifier: Modifier = Modifier,
) {
    // Merged (M13.2), for [StatTile]'s reason: "Connected to Notion" and "Last synced 3 minutes
    // ago" are one status in two lines, and the second on its own does not say what it is about.
    HabitsCard(modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) { }) {
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
                                if (summary.isConnected) {
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
                            if (summary.isConnected) R.string.sync_connected else R.string.sync_not_connected,
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = HabitsTheme.colors.text.primary,
                )
                Text(
                    text = summary.secondLine(),
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
private fun ConnectionSummary.secondLine(): String =
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

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 400)
@Composable
private fun ConnectionStatusCardPreview() {
    HabitsTheme {
        Column(
            modifier = Modifier.padding(Spacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        ) {
            ConnectionStatusCard(ConnectionSummary(isConnected = true, lastSynced = Elapsed.Minutes(3)))
            ConnectionStatusCard(ConnectionSummary(isConnected = true, isSyncing = true))
            ConnectionStatusCard(ConnectionSummary(isConnected = true, isWaitingForNetwork = true))
            ConnectionStatusCard(ConnectionSummary(isConnected = true, lastSynced = null))
            ConnectionStatusCard(ConnectionSummary(isConnected = false))
        }
    }
}
