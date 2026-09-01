package com.cristianprincipi.habits.feature.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Spacing
import com.cristianprincipi.habits.core.ui.HabitsEmpty
import com.cristianprincipi.habits.core.ui.HabitsEmptyReason
import com.cristianprincipi.habits.core.ui.preview.SampleHabits

/**
 * The Stats view, stateless — `docs/design-spec.md` §5.
 *
 * ⚠ **A scaffold: at M9.1 the content column is deliberately empty.** The four sections that fill
 * it each have their own step — the selector pills (M9.2), the year overview card (M9.3), the 2×2
 * stat tiles (M9.4) and the monthly chart (M9.5) — and every component they need already exists in
 * `core/designsystem` (`HabitChip`, `YearDotGrid`, `StatTile`). What this file establishes is the
 * one thing they share and could otherwise each get differently: the header, the three states, and
 * the 16dp padding and 16dp section spacing the design specifies.
 *
 * It takes no `onEvent` yet for the same reason. The single event is [StatsEvent.HabitSelected] and
 * the pill row is its only sender, so the parameter arrives with the pills in M9.2 rather than
 * sitting here unread.
 */
@Composable
fun StatsContent(
    state: StatsUiState,
    onOpenSyncStatus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        StatsTitle()

        when (state) {
            StatsUiState.Loading -> {
                // Nothing under the header, as on the other three tabs: a spinner for a Room query
                // that returns in a frame is a flash of chrome (`CLAUDE.md` §1).
                Unit
            }

            is StatsUiState.Empty -> {
                HabitsEmpty(
                    reason = state.reason,
                    onOpenSyncStatus = onOpenSyncStatus,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            is StatsUiState.Content -> {
                StatsSections(state = state, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/**
 * The header, which is one word — and **not the word on the tab**.
 *
 * The tab reads "Stats" and this reads "Statistics"; that asymmetry is the design's own
 * (`docs/design-spec.md` §1), not a mistake to tidy up. Inline rather than a `StatsHeader.kt`, for
 * `YearContent`'s reason: this view has no sub-nav and no `+`, so a file holding one `Text` would
 * imply chrome the screen does not have.
 */
@Composable
private fun StatsTitle() {
    Text(
        text = stringResource(R.string.screen_title_stats),
        style = MaterialTheme.typography.headlineSmall,
        color = HabitsTheme.colors.text.primary,
        // Horizontal padding only, and no bottom padding — the 16dp gap below comes from the
        // content column's own padding, which is what keeps this title level with Week's and
        // Month's. M8.7 found Year's sitting 4dp lower for exactly the missing version of this.
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.headerHorizontal),
    )
}

/**
 * The scrolling stack every section lands in.
 *
 * A plain `Column` with `verticalScroll`, not a `LazyColumn`: the design gives this screen four
 * sections and no list, so laziness would buy nothing and cost the 2×2 tile grid an awkward nesting.
 */
@Composable
private fun StatsSections(
    state: StatsUiState.Content,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardContentGap),
    ) {
        // M9.2 → the selector pills, M9.3 → the year overview card, M9.4 → the stat tiles,
        // M9.5 → the monthly completions chart. All four read `state`.
        StatsPlaceholder(state)
    }
}

/**
 * ⚠ **Temporary, and it exists to keep [state] read rather than to draw anything.**
 *
 * Deleted by M9.2, which is the first step with a real section to put here. Without it the
 * parameter is unused and the scaffold cannot be told apart from a screen whose query is broken.
 */
@Composable
private fun StatsPlaceholder(state: StatsUiState.Content) {
    Text(
        text = state.selected.habit.name,
        style = MaterialTheme.typography.titleMedium,
        color = HabitsTheme.colors.text.secondary,
    )
}

@Preview(name = "Stats — scaffold", backgroundColor = 0xFF111113, showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun StatsContentPreview() {
    HabitsTheme {
        StatsContent(
            state =
                StatsUiState.Content(
                    habits = SampleHabits.habits(),
                    selected = SampleHabits.stats(),
                ),
            onOpenSyncStatus = {},
        )
    }
}

@Preview(
    name = "Stats — empty, first import on its way",
    backgroundColor = 0xFF111113,
    showBackground = true,
    widthDp = 360,
    heightDp = 400,
)
@Composable
private fun StatsContentEmptyPreview() {
    HabitsTheme {
        StatsContent(
            state = StatsUiState.Empty(HabitsEmptyReason.SyncPending),
            onOpenSyncStatus = {},
        )
    }
}
