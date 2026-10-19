package com.cristianprincipi.habits.feature.week

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.datetime.accessibleDate
import com.cristianprincipi.habits.core.datetime.startOfWeek
import com.cristianprincipi.habits.core.datetime.weekdayLabels
import com.cristianprincipi.habits.core.designsystem.component.ReadingEntryDialog
import com.cristianprincipi.habits.core.designsystem.component.ValueStatus
import com.cristianprincipi.habits.core.designsystem.component.WeekHabitCard
import com.cristianprincipi.habits.core.designsystem.component.WeekMeasureCard
import com.cristianprincipi.habits.core.designsystem.component.prefillText
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Sizing
import com.cristianprincipi.habits.core.designsystem.theme.Spacing
import com.cristianprincipi.habits.core.ui.HabitsEmpty
import com.cristianprincipi.habits.core.ui.HabitsEmptyReason
import com.cristianprincipi.habits.core.ui.preview.SampleHabits
import com.cristianprincipi.habits.core.ui.rememberCurrentLocale
import com.cristianprincipi.habits.core.ui.resolve
import com.cristianprincipi.habits.core.ui.toDayStatus
import com.cristianprincipi.habits.domain.model.DayState
import com.cristianprincipi.habits.domain.model.HabitPeriod
import com.cristianprincipi.habits.domain.model.MeasureDay
import com.cristianprincipi.habits.domain.model.MeasureWeek
import com.cristianprincipi.habits.domain.model.PeriodCell
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/**
 * Everything the Week screen draws — `docs/design-spec.md` §3.
 *
 * Stateless and previewable, per `CLAUDE.md` §6: state comes down, [WeekEvent]s go up, and no
 * `ViewModel` reaches below [WeekScreen]. The previews at the bottom are the point of that split —
 * they render the real card against `SampleHabits`' real history, which is hundreds of completions
 * per habit rather than three hand-written ones.
 *
 * ### ⚠ The `‹ Aug 17 – Aug 23 ›` header is not here yet
 *
 * `docs/design-spec.md` §1 puts the sub-nav in the screen header and **M6.4 builds it**. Until then
 * there is no way to page, so three of the four [WeekEvent]s cannot be fired from the UI. The
 * ViewModel already handles all four.
 *
 * ### Insets and scrolling
 *
 * No insets taken here — the shell owns them (ADR-063). What this screen *does* own is its own
 * scrolling, which is ADR-065's second clause: the `Scaffold` hands down a correctly padded box, it
 * does not make the content fit inside it. Hence a `LazyColumn`.
 */
@Composable
fun WeekContent(
    state: WeekUiState,
    onEvent: (WeekEvent) -> Unit,
    // Navigation, so a plain lambda rather than a [WeekEvent] — `SettingsScreen` takes the same one
    // for the same destination. `CLAUDE.md` §6 routes *state changes* through the event interface;
    // where the user goes next is the graph's business and no ViewModel here has an opinion on it.
    onOpenSyncStatus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The design system does no date maths and no localisation, so both are resolved once here and
    // handed down — to the header for its `Aug 17 – Aug 23` label, and to the cards for their
    // weekday initials and spoken dates. `rememberCurrentLocale` carries the reason it is not the
    // two obvious one-liners; Lint rejects both.
    val locale = rememberCurrentLocale()

    Column(modifier = modifier.fillMaxSize()) {
        // Pinned above the list rather than scrolling with it — `docs/design-spec.md` §1's diagram.
        // It takes the whole state, not a title: the sub-nav only exists when there is a week.
        WeekHeader(week = state as? WeekUiState.Content, onEvent = onEvent, locale = locale)

        when (state) {
            // Room is local and fast, so this is a frame or two. Drawing nothing beats a spinner
            // that flashes — `CLAUDE.md` §1 is explicit that no screen is spinner-gated on data.
            WeekUiState.Loading -> {
                Unit
            }

            is WeekUiState.Empty -> {
                HabitsEmpty(
                    reason = state.reason,
                    onOpenSyncStatus = onOpenSyncStatus,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            is WeekUiState.Content -> {
                WeekList(
                    state = state,
                    locale = locale,
                    onEvent = onEvent,
                    modifier = Modifier.fillMaxSize(),
                )
                state.readingEntry?.let { entry ->
                    ReadingEntryDialog(
                        entry = entry,
                        locale = locale,
                        onTextChange = { text -> onEvent(WeekEvent.ReadingTextChanged(text)) },
                        onSave = { onEvent(WeekEvent.ReadingSaved) },
                        onClear = { onEvent(WeekEvent.ReadingCleared) },
                        onDismiss = { onEvent(WeekEvent.ReadingDismissed) },
                    )
                }
            }
        }
    }
}

/**
 * The list of habit cards.
 *
 * A lazy grid with a stable key per habit (`CLAUDE.md` §6). The key is the habit's id rather than
 * its position, so paging to another week — which replaces every card's *contents* but not the
 * habits themselves — reuses the rows instead of rebuilding them.
 *
 * ⭐ **A grid rather than a column since M12.8**, and on a phone in portrait it is the same thing:
 * `GridCells.Adaptive` resolves to one column at any width a phone has. What it buys is landscape,
 * where this screen showed **one card, clipped** (M6.11) with its pills stretched to twice the width
 * the design draws them at.
 */
@Composable
private fun WeekList(
    state: WeekUiState.Content,
    locale: Locale,
    onEvent: (WeekEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = remember(locale) { weekdayLabels(locale) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(Sizing.habitCardMinWidth),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        // ⭐ M12.8. `Adaptive`, not `Fixed(1)`: at 411dp portrait the arithmetic yields exactly
        // one column, which is what the design draws, and the extra width of a landscape or tablet
        // window buys a second card rather than a stretched one. See `Sizing.habitCardMinWidth` for
        // why that floor is the week pill's number.
        // ⭐ M12.5 — see `Motion`'s closing note for why this is a platform spring and not a token.
        items(items = state.habits, key = { period -> period.habit.id.value }) { period ->
            WeekHabitRow(
                period = period,
                dayLabels = labels,
                locale = locale,
                onEvent = onEvent,
                modifier = Modifier.animateItem(),
            )
        }
        // M16.1 — the numbers come after every habit, so the check-off list above stays unbroken.
        // ⚠ Keyed with a prefix: a measure id and a habit id are both Notion property ids, distinct in
        // practice, but one grid cannot be allowed to hold two items with the same key.
        items(items = state.measures, key = { week -> "measure:${week.measure.id.value}" }) { week ->
            WeekMeasureRow(
                week = week,
                dayLabels = labels,
                locale = locale,
                onEvent = onEvent,
                modifier = Modifier.animateItem(),
            )
        }
    }
}

/**
 * One habit's card, with the domain mapped onto what the design system draws.
 *
 * ### The card's second line
 *
 * ⚠ **It is not what the design asked for, because what it asked for no longer exists.**
 * `docs/design-spec.md` §3 gives the card a *description* — the prototype's own example is
 * `"15 min practice"` — and **ADR-027 deleted `Habit.description`**: Notion has nowhere to keep one
 * and ADR-010 makes the habit set read-only, so there is neither a source nor an editor for it.
 *
 * Leaving the slot blank is not free either. `HabitCardHeader` draws that line unconditionally, so
 * an empty string still holds the space and the card reads as though something failed to load. The
 * slot is therefore filled with the most useful fact a *week* view has: how much of it is done.
 *
 * **Undesigned — the user should look at it (M2.8, and `Plan.md` M6.11).** The alternative is
 * dropping the second line and letting the card get shorter, which changes a shape the design does
 * specify. Whichever wins, M8's Month and Year cards have the same hole.
 *
 * ⭐ **The count itself moved to `HabitPeriod.completedCount` in M15.6**, because the home-screen
 * widget prints this same sentence and two counts of one thing can disagree.
 */
@Composable
private fun WeekHabitRow(
    period: HabitPeriod,
    dayLabels: List<String>,
    locale: Locale,
    onEvent: (WeekEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    WeekHabitCard(
        modifier = modifier,
        name = period.habit.name,
        description =
            stringResource(R.string.habit_card_summary, period.completedCount, period.cells.size),
        emoji = period.habit.emoji,
        habitColor = period.habit.color.resolve(),
        days = period.cells.map { cell -> cell.toDayStatus() },
        dayLabels = dayLabels,
        dayContentDescriptions = period.spokenLabels(locale),
        todayIndex = period.todayIndex,
        onToggleDay = { index -> period.toggleAt(index)?.let(onEvent) },
    )
}

/**
 * One tracked number's card — M16.1, read-only.
 *
 * ### The card's second line is the week's average
 *
 * It is the habit card's *"5 of 7"* for a number: the one fact the week as a whole has. Formatted
 * through Stats' own strings, so the two screens print a mean to the same one decimal place, and a
 * week without readings says so instead of printing a zero.
 *
 * ### ⚠ A cell drops a trailing ".0", and the average does not
 *
 * A cell is one reading as it was entered, and `8.0` in a 44dp column is width spent on nothing. A mean is a computed figure, and Stats has always
 * printed it to one place. Both round half-up, as `String.format` does, so a cell and a tile never
 * round one value two ways.
 *
 * ### ⭐ A day that has happened can be tapped — M16.2b
 *
 * It opens [ReadingEntryDialog] with the day's value **as stored, not as drawn**: the cell shows one
 * decimal, and a field that started at `83` for an `82.95` would round the value the moment it was
 * saved untouched. A future day is inert, as its pill above is.
 */
@Composable
private fun WeekMeasureRow(
    week: MeasureWeek,
    dayLabels: List<String>,
    locale: Locale,
    onEvent: (WeekEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measure = week.measure
    val formatter = remember(locale) { cellFormat(locale) }
    val missing = stringResource(R.string.week_measure_missing)
    val average = week.average.value
    val description =
        when {
            average == null -> {
                stringResource(R.string.week_measure_no_entries)
            }

            measure.unit.isBlank() -> {
                stringResource(R.string.week_measure_average, stringResource(R.string.stats_measure_value, average))
            }

            else -> {
                stringResource(
                    R.string.week_measure_average,
                    stringResource(R.string.stats_measure_value_with_unit, average, measure.unit),
                )
            }
        }
    val texts = week.days.map { day -> day.value?.let { value -> formatter.format(value) } ?: missing }

    WeekMeasureCard(
        modifier = modifier,
        name = measure.name,
        description = description,
        emoji = measure.emoji,
        color = measure.color.resolve(),
        texts = texts,
        statuses = week.days.map { day -> day.status() },
        dayLabels = dayLabels,
        dayContentDescriptions = week.spokenLabels(texts, locale),
        todayIndex = week.todayIndex,
        editable = week.days.map { day -> !day.isFuture },
        editLabel = stringResource(R.string.week_reading_edit_action),
        onCellTap = { index ->
            week.days.getOrNull(index)?.let { day ->
                onEvent(WeekEvent.ReadingCellTapped(measure.id, day.date, prefillText(day.value, locale)))
            }
        },
    )
}

/** At most one decimal, none when it is zero — see [WeekMeasureRow]. */
private fun cellFormat(locale: Locale): NumberFormat =
    NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = 1
        roundingMode = RoundingMode.HALF_UP
    }

/** A reading outranks "future" — see [MeasureDay.isFuture]. */
private fun MeasureDay.status(): ValueStatus =
    when {
        value != null -> ValueStatus.RECORDED
        isFuture -> ValueStatus.FUTURE
        else -> ValueStatus.MISSING
    }

/**
 * What a screen reader says for each of a number card's seven cells: the measure, the date, and
 * the value with its unit — or "no entry", so a missing day is heard as missing rather than as a
 * dash. A future day is the date alone, as its pill above is.
 */
@Composable
private fun MeasureWeek.spokenLabels(
    texts: List<String>,
    locale: Locale,
): List<String> {
    val withValue = stringResource(R.string.week_measure_cell_description)
    val withUnit = stringResource(R.string.week_measure_value_with_unit)
    val empty = stringResource(R.string.week_measure_cell_empty)
    val dateOnly = stringResource(R.string.week_pill_description)
    return remember(days, texts, locale, measure, withValue, withUnit, empty, dateOnly) {
        days.mapIndexed { index, day ->
            val date = accessibleDate(day.date, locale)
            when (day.status()) {
                ValueStatus.FUTURE -> {
                    dateOnly.format(measure.name, date)
                }

                ValueStatus.MISSING -> {
                    withValue.format(measure.name, date, empty)
                }

                ValueStatus.RECORDED -> {
                    val value = texts[index]
                    val spoken = if (measure.unit.isBlank()) value else withUnit.format(value, measure.unit)
                    withValue.format(measure.name, date, spoken)
                }
            }
        }
    }
}

/**
 * The event a tap on pill [index] means, or `null` when there is no day behind it.
 *
 * The **wanted value, not a flip** — see [WeekEvent.DayToggled].
 *
 * ⚠ **Null rather than a stand-in date.** A padding cell cannot occur in a week (`weekWindow`
 * produces none, by definition), but the shared [PeriodCell] type allows one. An earlier version
 * substituted `LocalDate.EPOCH` — which Android Lint rejected outright, because that constant is
 * **API 34** and `minSdk` is 26, so it would have crashed on most devices in the field. Emitting
 * nothing is the better answer regardless: writing 1 January 1970 into Room to paper over an
 * impossible cell would push a nonsense day to the user's Notion database.
 */
private fun HabitPeriod.toggleAt(index: Int): WeekEvent.DayToggled? {
    val cell = cells.getOrNull(index) as? PeriodCell.Day ?: return null
    return WeekEvent.DayToggled(
        habitId = habit.id,
        date = cell.date,
        completed = cell.state != DayState.COMPLETED,
    )
}

/**
 * What a screen reader announces for each of a card's seven pills.
 *
 * `WeekPills`' KDoc asks for **the date, not the state**: the pill is already a checkbox, so Android
 * announces "ticked" / "not ticked" itself and repeating it here would say it twice.
 *
 * ⭐ **It also names the habit**, which that KDoc does not ask for and which is the right call
 * anyway. Semantics are not merged at the card, so all thirty-five pills on this screen are
 * separate nodes; a user moving control by control would otherwise hear "Monday, 17 August 2026"
 * five times over with nothing to say which habit each belonged to. The card's visible header is
 * only context if you arrived through it.
 *
 * It is also what makes a pill uniquely addressable in a test — see `WeekRenderTest`.
 */
@Composable
private fun HabitPeriod.spokenLabels(locale: Locale): List<String> {
    val pattern = stringResource(R.string.week_pill_description)
    return remember(cells, locale, habit.name, pattern) {
        cells.map { cell ->
            when (cell) {
                PeriodCell.Padding -> ""
                is PeriodCell.Day -> pattern.format(habit.name, accessibleDate(cell.date, locale))
            }
        }
    }
}

// --- previews ---------------------------------------------------------------------------------

@Preview(name = "Week", backgroundColor = 0xFF111113, showBackground = true, widthDp = 360, heightDp = 1100)
@Composable
private fun WeekContentPreview() {
    HabitsTheme {
        WeekContent(
            state =
                WeekUiState.Content(
                    weekStart = startOfWeek(SampleHabits.referenceDate),
                    habits = SampleHabits.weekPeriods(),
                    isCurrentWeek = true,
                    currentYear = SampleHabits.referenceDate.year,
                    measures = SampleHabits.measureWeeks(),
                ),
            onEvent = {},
            onOpenSyncStatus = {},
        )
    }
}

@Preview(
    name = "Week — empty, first import on its way",
    backgroundColor = 0xFF111113,
    showBackground = true,
    widthDp = 360,
    heightDp = 400,
)
@Composable
private fun WeekContentEmptyPendingPreview() {
    HabitsTheme {
        WeekContent(state = WeekUiState.Empty(HabitsEmptyReason.SyncPending), onEvent = {}, onOpenSyncStatus = {})
    }
}

@Preview(
    name = "Week — empty, first import failed",
    backgroundColor = 0xFF111113,
    showBackground = true,
    widthDp = 360,
    heightDp = 400,
)
@Composable
private fun WeekContentEmptyFailedPreview() {
    HabitsTheme {
        WeekContent(state = WeekUiState.Empty(HabitsEmptyReason.SyncFailed), onEvent = {}, onOpenSyncStatus = {})
    }
}
