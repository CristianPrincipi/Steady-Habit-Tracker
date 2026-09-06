package com.cristianprincipi.habits.feature.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.datetime.shortMonthLabel
import com.cristianprincipi.habits.core.designsystem.component.ChartPoint
import com.cristianprincipi.habits.core.designsystem.component.HabitChip
import com.cristianprincipi.habits.core.designsystem.component.HabitsCard
import com.cristianprincipi.habits.core.designsystem.component.MINIMUM_CHART_POINTS
import com.cristianprincipi.habits.core.designsystem.component.MonthlyChart
import com.cristianprincipi.habits.core.designsystem.component.SectionEyebrow
import com.cristianprincipi.habits.core.designsystem.component.StatTile
import com.cristianprincipi.habits.core.designsystem.component.StatTileIcon
import com.cristianprincipi.habits.core.designsystem.component.YearDotGrid
import com.cristianprincipi.habits.core.designsystem.component.everyWordFits
import com.cristianprincipi.habits.core.designsystem.component.readingWidth
import com.cristianprincipi.habits.core.designsystem.icon.Lucide
import com.cristianprincipi.habits.core.designsystem.icon.LucideGlyph
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Sizing
import com.cristianprincipi.habits.core.designsystem.theme.Spacing
import com.cristianprincipi.habits.core.ui.HabitsEmpty
import com.cristianprincipi.habits.core.ui.HabitsEmptyReason
import com.cristianprincipi.habits.core.ui.preview.SampleHabits
import com.cristianprincipi.habits.core.ui.rememberCurrentLocale
import com.cristianprincipi.habits.core.ui.resolve
import com.cristianprincipi.habits.core.ui.toDayStatus
import com.cristianprincipi.habits.domain.model.Habit
import com.cristianprincipi.habits.domain.model.HabitId
import com.cristianprincipi.habits.domain.usecase.HabitStats
import java.util.Locale

/**
 * The Stats view, stateless — `docs/design-spec.md` §5.
 *
 * **Every section the design draws is here**: the pills (M9.2), the year overview (M9.3), the four
 * stat tiles (M9.4) and the monthly chart (M9.5). What this file establishes is the one thing they
 * all share and could otherwise each get differently: the header, the three states, and the 16dp
 * padding and 16dp section spacing the design specifies.
 *
 * ⭐ **This had a `numerals` parameter and no longer does.** It existed only to settle ADR-021's
 * open question, which the user closed at M9.8 in favour of the design's DM Sans — so the parameter,
 * the `NumeralStyle` enum and the whole second set of styles went with the answer.
 */
@Composable
fun StatsContent(
    state: StatsUiState,
    onEvent: (StatsEvent) -> Unit,
    onOpenSyncStatus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.readingWidth().fillMaxSize()) {
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
                StatsSections(
                    state = state,
                    onEvent = onEvent,
                    modifier = Modifier.fillMaxSize(),
                )
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
        StatTiles(stats = state.selected)
        MonthlyCompletionsCard(stats = state.selected)
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
        // ⭐ M12.5 — see `Motion`'s closing note for why this is a platform spring and not a token.
        items(items = habits, key = { habit -> habit.id.value }) { habit ->
            HabitChip(
                emoji = habit.emoji,
                name = habit.name,
                habitColor = habit.color.resolve(),
                selected = habit.id == selectedId,
                onClick = { onSelect(habit.id) },
                modifier = Modifier.animateItem(),
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

/**
 * The four stat tiles — `docs/design-spec.md` §5.3.
 *
 * Four numbers from two values: [HabitStats.rate] is the first row and [HabitStats.streak] the
 * second. `StatTile` and `StatTileIcon` are M2's and the glyphs are ADR-024's, so this is an
 * arrangement and nothing more.
 *
 * A `Row` of `Row`s rather than a `LazyVerticalGrid`: there are exactly four tiles, always, and a
 * lazy grid inside a vertically scrolling `Column` needs a fixed height it has no business having.
 *
 * ⚠ **These digits jitter, and the user chose that knowingly at M9.8.** The design asks for tabular
 * figures and `HabitsTextStyles.statValue` duly requests `tnum` — but ADR-021 §2 read DM Sans
 * 4.004's tables out of the bundled binary and the feature **is not in the font**: `1` is 342 units
 * against `0` at 656. The prototype's own `tabular-nums` is equally inert. The stable-width
 * alternative was built, rendered beside this one, and lost on its slashed zero (ADR-099).
 */
@Composable
private fun StatTiles(
    stats: HabitStats,
    modifier: Modifier = Modifier,
) {
    val color = stats.habit.color.resolve()
    val tiles =
        listOf(
            StatTileSpec(R.string.stats_tile_completed_days, Lucide.Hash) {
                stringResource(R.string.stats_count_value, stats.rate.completedDays)
            },
            StatTileSpec(R.string.stats_tile_completion_rate, Lucide.Percent) {
                stringResource(R.string.percent_value, stats.rate.percent)
            },
            StatTileSpec(R.string.stats_tile_current_streak, Lucide.Flame) {
                stringResource(R.string.stats_count_value, stats.streak.current)
            },
            StatTileSpec(R.string.stats_tile_best_streak, Lucide.Trophy) {
                stringResource(R.string.stats_count_value, stats.streak.best)
            },
        )

    // ⚠⚠ M13.3 — **at 200% font these read "Complete/d days" and "Completio/n rate".** Two 138dp
    // columns on a 320dp window cannot hold "Completion" at 22sp, and Compose breaks a word it
    // cannot fit wherever the character boundary lands.
    //
    // ⚠ **Shrinking would be the wrong fix here, and that is the difference from the weekday row.**
    // These labels are content: M13.2 merged each into its tile's spoken name, so a screen reader
    // announces "Completion rate, 87%" as one thing. They need **room**, not a smaller font.
    //
    // ⭐ **So the 2×2 becomes a single column when a label will not fit half the width.** Measured
    // against the width the text actually gets — the tile minus its card padding — so the answer
    // follows a copy change or a longer translation without anyone editing a threshold, and nothing
    // moves at any scale where the design's grid fits.
    BoxWithConstraints(modifier = modifier) {
        val perTile = (maxWidth - Spacing.cardGap) / 2 - Spacing.cardPadding * 2
        val twoColumns =
            tiles.all { tile ->
                everyWordFits(stringResource(tile.label), MaterialTheme.typography.labelSmall, perTile)
            }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
            if (twoColumns) {
                tiles.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
                        pair.forEach { tile -> Tile(spec = tile, color = color) }
                    }
                }
            } else {
                tiles.forEach { tile ->
                    StatTile(
                        label = stringResource(tile.label),
                        value = tile.value(),
                        habitColor = color,
                        modifier = Modifier.fillMaxWidth(),
                        icon = { StatTileIcon(glyph = tile.glyph, habitColor = color) },
                    )
                }
            }
        }
    }
}

/**
 * One of the four numbers, as data rather than as a call — so the same four can be laid out two ways.
 *
 * @param value read inside composition because every one of them is a `stringResource`.
 */
private data class StatTileSpec(
    val label: Int,
    val glyph: LucideGlyph,
    val value: @Composable () -> String,
)

/**
 * The "Monthly completions" card — `docs/design-spec.md` §5.4.
 *
 * The card is three lines; what it owns is the **words**, because [MonthlyChart] has none. Three
 * things cross the boundary here and all three need a locale the design system does not hold: the
 * month name under each point, the digits up the axis, and the sentence a screen reader hears in
 * place of the drawing.
 *
 * ⭐ **`stats_count_value` formats the axis, the same resource the four tiles above use.** A bare
 * `Int.toString()` would print ASCII digits up the side of a chart whose tiles had been localised —
 * the mismatch `strings.xml` warns about, one card apart.
 *
 * ### ⚠ M9.6: the card stays and the sentence goes inside it
 *
 * M9.5 hid this card outright when there was nothing to chart, and put the branch in the section
 * list so that this step would have one place to change it. It is changed. **A section that appears
 * from nowhere a month later is a screen nobody can learn**, and the card with a line in it is also
 * what the user's own standing preference asks for — say what happens next, in place, rather than
 * removing the thing.
 *
 * The threshold is [MINIMUM_CHART_POINTS] and it is **structural rather than chosen**: a line needs
 * two points. It catches both cases at once — a habit in its first calendar month, and the sync
 * anomaly of an inferred start date in the future, which yields no bucket at all.
 */
@Composable
private fun MonthlyCompletionsCard(
    stats: HabitStats,
    modifier: Modifier = Modifier,
) {
    val locale = rememberCurrentLocale()
    val points =
        remember(stats.months, locale) {
            stats.months.map { month ->
                ChartPoint(label = shortMonthLabel(month.month, locale), value = month.completions)
            }
        }

    HabitsCard(modifier = modifier) {
        SectionEyebrow(text = stringResource(R.string.stats_monthly_eyebrow))
        if (points.size < MINIMUM_CHART_POINTS) {
            Text(
                text = stringResource(R.string.stats_chart_not_yet),
                style = MaterialTheme.typography.bodyMedium,
                color = HabitsTheme.colors.text.secondary,
            )
        } else {
            Chart(
                points = points,
                habitColor = stats.habit.color.resolve(),
                locale = locale,
                lastPointIsPartial = stats.lastMonthIsUnfinished,
            )
        }
    }
}

/** The drawing itself, split out so the branch above reads as the decision it is. */
@Composable
private fun Chart(
    points: List<ChartPoint>,
    habitColor: Color,
    locale: Locale,
    lastPointIsPartial: Boolean,
) {
    val countPattern = stringResource(R.string.stats_count_value)
    val formatValue = remember(countPattern, locale) { { value: Int -> countPattern.format(locale, value) } }

    MonthlyChart(
        points = points,
        habitColor = habitColor,
        formatValue = formatValue,
        contentDescription = spokenChart(points, locale),
        lastPointIsPartial = lastPointIsPartial,
    )
}

/**
 * The chart read aloud — "Monthly completions: Sep 12, Oct 18, …".
 *
 * Thirteen short pairs, which is long for a `contentDescription` and still the right call: the
 * alternative is [MonthlyChart]'s KDoc case for clearing semantics outright, and it does not apply
 * here because no other element on this screen prints a monthly figure.
 */
@Composable
private fun spokenChart(
    points: List<ChartPoint>,
    locale: Locale,
): String {
    val pattern = stringResource(R.string.stats_chart_description)
    val monthPattern = stringResource(R.string.stats_chart_month)
    val separator = stringResource(R.string.stats_chart_month_separator)
    return remember(points, locale, pattern, monthPattern, separator) {
        val months = points.joinToString(separator) { monthPattern.format(locale, it.label, it.value) }
        pattern.format(locale, months)
    }
}

/** One tile, at half the row. Split out so the four calls above read as a table. */
@Composable
private fun RowScope.Tile(
    spec: StatTileSpec,
    color: Color,
) {
    StatTile(
        label = stringResource(spec.label),
        value = spec.value(),
        habitColor = color,
        modifier = Modifier.weight(1f),
        icon = { StatTileIcon(glyph = spec.glyph, habitColor = color) },
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
