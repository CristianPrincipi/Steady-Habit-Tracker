package com.cristianprincipi.habits.feature.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.designsystem.component.HabitChip
import com.cristianprincipi.habits.core.designsystem.component.HabitsCard
import com.cristianprincipi.habits.core.designsystem.component.SectionEyebrow
import com.cristianprincipi.habits.core.designsystem.component.YearDotGrid
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Sizing
import com.cristianprincipi.habits.core.designsystem.theme.Spacing
import com.cristianprincipi.habits.core.ui.HabitsEmpty
import com.cristianprincipi.habits.core.ui.HabitsEmptyReason
import com.cristianprincipi.habits.core.ui.preview.SampleHabits
import com.cristianprincipi.habits.core.ui.resolve
import com.cristianprincipi.habits.core.ui.toDayStatus
import com.cristianprincipi.habits.domain.model.Habit
import com.cristianprincipi.habits.domain.model.HabitId
import com.cristianprincipi.habits.domain.usecase.HabitStats

/**
 * The Stats view, stateless — `docs/design-spec.md` §5.
 *
 * ⚠ **Still filling: M9.2 added the selector pills and M9.3 the year overview.** The two sections
 * below them have their own steps — the 2×2 stat tiles (M9.4) and the monthly chart (M9.5) — and
 * `StatTile` already exists in `core/designsystem`. What this file establishes is the one thing
 * every section shares and could otherwise each get differently: the header, the three states, and
 * the 16dp padding and 16dp section spacing the design specifies.
 */
@Composable
fun StatsContent(
    state: StatsUiState,
    onEvent: (StatsEvent) -> Unit,
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
                StatsSections(state = state, onEvent = onEvent, modifier = Modifier.fillMaxSize())
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
    onEvent: (StatsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardContentGap),
    ) {
        HabitSelector(
            habits = state.habits,
            selectedId = state.selectedId,
            onSelect = { id -> onEvent(StatsEvent.HabitSelected(id)) },
        )
        YearOverviewCard(stats = state.selected)
        // M9.4 -> the stat tiles, M9.5 -> the monthly completions chart. Both read
        // `state.selected` too.
    }
}

/**
 * The habit selector - `docs/design-spec.md` §5.1.
 *
 * ⭐ **`HabitChip` is the pill**, unchanged from M2: solid habit colour when selected, a 12.5% tint
 * of it when not, `{emoji} {name}`, and a 48dp touch target under a shorter drawn height. This row
 * only arranges them.
 *
 * ⚠ **`selectableGroup()` is the row's own job, and `HabitChip` says so in its KDoc.** Each chip is
 * `selectable`, which tells a screen reader whether that chip is chosen; only the group tells it
 * *how many of what* - "2 of 5" - and makes the row one stop in the traversal rather than five. It
 * is a semantics-only modifier, so nothing on screen moves if it is dropped, which is exactly the
 * kind of claim M8.5 found nothing was keeping true.
 *
 * A `LazyRow`, keyed by habit id: the design has this row scroll horizontally, and five habits at
 * their full names already exceed a phone's width. It scrolls **within the screen's 16dp padding**
 * rather than bleeding to the edges, which is what the prototype's padded overflow container does.
 */
@Composable
private fun HabitSelector(
    habits: List<Habit>,
    selectedId: HabitId,
    onSelect: (HabitId) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap),
    ) {
        items(items = habits, key = { habit -> habit.id.value }) { habit ->
            HabitChip(
                emoji = habit.emoji,
                name = habit.name,
                habitColor = habit.color.resolve(),
                selected = habit.id == selectedId,
                onClick = { onSelect(habit.id) },
            )
        }
    }
}

/**
 * The "Year overview" card — `docs/design-spec.md` §5.2.
 *
 * An eyebrow and a grid, and nothing else: the percentage that sits beside the Year *tab*'s grid
 * belongs to M9.4's "completion rate" tile here, and printing it twice on one screen would be two
 * places to keep in step.
 *
 * ⭐ **`Sizing.yearDotLarge` (6dp) rather than the Year tab's 4dp**, which is the design's own
 * figure and the reason this card exists as a separate rendering of the same component: a whole
 * screen for one habit can afford the room that five stacked cards cannot.
 *
 * ⚠ **`toDayStatus()` runs here, at the call site, and ADR-090 wanted it hoisted out.** M9.3
 * measured the case that note was written for and found it does not arise on this screen — see
 * `StatsRedrawTest` and ADR-094. The hoist stays unbuilt on purpose: it would make `YearDotGrid`
 * skippable at the price of allocating 371 entries on every emission, and nothing here recomposes
 * without the cells changing anyway.
 */
@Composable
private fun YearOverviewCard(
    stats: HabitStats,
    modifier: Modifier = Modifier,
) {
    HabitsCard(modifier = modifier) {
        SectionEyebrow(text = stringResource(R.string.stats_year_overview_eyebrow))
        YearDotGrid(
            days = stats.period.cells.map { cell -> cell.toDayStatus() },
            habitColor = stats.habit.color.resolve(),
            dotSize = Sizing.yearDotLarge,
        )
    }
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
            onEvent = {},
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
            onEvent = {},
            onOpenSyncStatus = {},
        )
    }
}
