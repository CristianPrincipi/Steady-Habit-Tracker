package com.cristianprincipi.habits.feature.week

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
import com.cristianprincipi.habits.core.ui.rememberCurrentLocale
import com.cristianprincipi.habits.core.ui.resolve
import com.cristianprincipi.habits.core.ui.toDayStatus
import com.cristianprincipi.habits.domain.model.DayState
import com.cristianprincipi.habits.domain.model.HabitPeriod
import com.cristianprincipi.habits.domain.model.PeriodCell
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
                WeekEmpty(
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
            }
        }
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
    locale: Locale,
    onEvent: (WeekEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
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
 * ⚠ **The copy is UNDESIGNED and goes to the user at M6.11** (M2.8). What is settled is the
 * *shape*: two sentences, and no "add a habit" button in either — ADR-010 removed habit creation,
 * so there is no `+` this app could ever honour.
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
private fun WeekEmpty(
    reason: WeekEmptyReason,
    onOpenSyncStatus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // ⚠ Centred, and `EmptyState` is left to size itself. Handing it `fillMaxSize` is what the
    // first build did, and on a real screen — rather than the 400dp preview — that pins three lines
    // to the very top with most of a phone of black under them, which reads as a load that stopped
    // halfway rather than as a finished screen. Seen on the emulator, fixed in the same turn
    // (`CLAUDE.md` §7).
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (reason) {
            WeekEmptyReason.SyncPending -> {
                EmptyState(
                    // A ticked box, because that is literally what a habit is upstream: a checkbox
                    // property on a Notion page (`docs/notion-schema.md`).
                    emoji = stringResource(R.string.week_empty_pending_emoji),
                    title = stringResource(R.string.week_empty_pending_title),
                    body = stringResource(R.string.week_empty_pending_body),
                )
            }

            WeekEmptyReason.SyncFailed -> {
                EmptyState(
                    emoji = stringResource(R.string.week_empty_failed_emoji),
                    title = stringResource(R.string.week_empty_failed_title),
                    body = stringResource(R.string.week_empty_failed_body),
                    actionLabel = stringResource(R.string.week_empty_failed_action),
                    onAction = onOpenSyncStatus,
                )
            }
        }
    }
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
        WeekContent(state = WeekUiState.Empty(WeekEmptyReason.SyncPending), onEvent = {}, onOpenSyncStatus = {})
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
        WeekContent(state = WeekUiState.Empty(WeekEmptyReason.SyncFailed), onEvent = {}, onOpenSyncStatus = {})
    }
}
