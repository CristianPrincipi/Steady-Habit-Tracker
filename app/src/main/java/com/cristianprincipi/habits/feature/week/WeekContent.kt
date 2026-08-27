package com.cristianprincipi.habits.feature.week

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.datetime.accessibleDate
import com.cristianprincipi.habits.core.datetime.startOfWeek
import com.cristianprincipi.habits.core.datetime.weekdayLabels
import com.cristianprincipi.habits.core.designsystem.component.EmptyState
import com.cristianprincipi.habits.core.designsystem.component.WeekHabitCard
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Spacing
import com.cristianprincipi.habits.core.ui.preview.SampleHabits
import com.cristianprincipi.habits.core.ui.resolve
import com.cristianprincipi.habits.core.ui.toDayStatus
import com.cristianprincipi.habits.domain.model.DayState
import com.cristianprincipi.habits.domain.model.HabitPeriod
import com.cristianprincipi.habits.domain.model.PeriodCell
import java.util.Locale
import androidx.compose.ui.text.intl.Locale as ComposeLocale

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
    modifier: Modifier = Modifier,
) {
    when (state) {
        // Room is local and fast, so this is a frame or two. Drawing nothing beats a spinner that
        // flashes — `CLAUDE.md` §1 is explicit that no screen is ever spinner-gated on data.
        WeekUiState.Loading -> Unit

        WeekUiState.Empty -> WeekEmpty(modifier = modifier)

        is WeekUiState.Content -> WeekList(state = state, onEvent = onEvent, modifier = modifier)
    }
}

/**
 * The list of habit cards.
 *
 * `LazyColumn` with a stable key per habit (`CLAUDE.md` §6). The key is the habit's id rather than
 * its position, so paging to another week — which replaces every card's *contents* but not the
 * habits themselves — reuses the rows instead of rebuilding them.
 */
@Composable
private fun WeekList(
    state: WeekUiState.Content,
    onEvent: (WeekEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The design system does no date maths and no localisation, so both are resolved here and
    // handed down.
    //
    // ⚠ `androidx.compose.ui.text.intl.Locale.current`, not `LocalConfiguration.current.locales[0]`
    // and not `java.util.Locale.getDefault()`. Lint's `NonObservableLocale` rejects both: the first
    // reads a Configuration field that composition does not observe, the second is read once and
    // then goes stale for the life of the process. This one is state-backed, so the labels and the
    // spoken dates actually change when the user changes their language.
    val locale = remember(ComposeLocale.current) { Locale.forLanguageTag(ComposeLocale.current.toLanguageTag()) }
    val labels = remember(locale) { weekdayLabels(locale) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        items(items = state.habits, key = { period -> period.habit.id.value }) { period ->
            WeekHabitRow(period = period, dayLabels = labels, locale = locale, onEvent = onEvent)
        }
    }
}

/** One habit's card, with the domain mapped onto what the design system draws. */
@Composable
private fun WeekHabitRow(
    period: HabitPeriod,
    dayLabels: List<String>,
    locale: Locale,
    onEvent: (WeekEvent) -> Unit,
) {
    WeekHabitCard(
        name = period.habit.name,
        description =
            stringResource(R.string.week_card_summary, period.completedCount, period.cells.size),
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
 * The card's second line.
 *
 * ⚠ **This is not what the design asked for, because what it asked for no longer exists.**
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
 */
private val HabitPeriod.completedCount: Int
    get() = cells.count { cell -> cell is PeriodCell.Day && cell.state == DayState.COMPLETED }

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

/**
 * No active habits at all — **not "nothing this week"**, which cannot happen (see [WeekUiState]).
 *
 * ⚠ **M6.8 owns this and it is undesigned.** What stands here is M2's `EmptyState` with honest copy
 * and no call to action, because the obvious CTA does not exist: ADR-010 removed habit creation, so
 * there is no `+` to offer. The wording has to point at Notion, and M6.8 is where that gets settled
 * with the user — including whether to tell apart "the mapping chose no habit columns" from "every
 * mapped column has since disappeared upstream".
 */
@Composable
private fun WeekEmpty(modifier: Modifier = Modifier) {
    EmptyState(
        // A ticked box, because that is literally what a habit is upstream: a checkbox property on
        // a Notion page (`docs/notion-schema.md`). `EmptyState` requires one — its own KDoc already
        // reached ADR-010's conclusion, that "no habits yet" is a sentence rather than a button.
        emoji = stringResource(R.string.week_empty_emoji),
        title = stringResource(R.string.week_empty_title),
        body = stringResource(R.string.week_empty_body),
        modifier = modifier.fillMaxSize(),
    )
}

// --- previews ---------------------------------------------------------------------------------

@Preview(name = "Week", backgroundColor = 0xFF111113, showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun WeekContentPreview() {
    HabitsTheme {
        WeekContent(
            state =
                WeekUiState.Content(
                    weekStart = startOfWeek(SampleHabits.referenceDate),
                    habits = SampleHabits.weekPeriods(),
                    isCurrentWeek = true,
                ),
            onEvent = {},
        )
    }
}

@Preview(name = "Week — empty", backgroundColor = 0xFF111113, showBackground = true, widthDp = 360, heightDp = 400)
@Composable
private fun WeekContentEmptyPreview() {
    HabitsTheme {
        WeekContent(state = WeekUiState.Empty, onEvent = {})
    }
}
