package com.cristianprincipi.habits.feature.week

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cristianprincipi.habits.core.datetime.DAYS_PER_WEEK
import com.cristianprincipi.habits.core.datetime.startOfWeek
import com.cristianprincipi.habits.domain.model.HabitId
import com.cristianprincipi.habits.domain.model.HabitPeriod
import com.cristianprincipi.habits.domain.usecase.GetHabitsForWeek
import com.cristianprincipi.habits.domain.usecase.ToggleCompletion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Drives the Week screen — the landing tab, and the only place a habit is checked off (ADR-009).
 *
 * One input that changes, one that streams. The **reference week** is a `MutableStateFlow` here
 * rather than a parameter, because `‹` and `›` have to move it and nothing outside this screen has
 * an opinion about which week is showing; `flatMapLatest` then swaps the underlying query whenever
 * it moves, cancelling the week the user just paged away from.
 *
 * `GetHabitsForWeek` does the real work — it is the thing that joins habits to completions and
 * decides what each of the seven cells means. This class picks a week, labels the result,
 * forwards five gestures, and says once what each check-off changed.
 *
 * ### ⚠ Two facts here are true only at the moment they are emitted
 *
 * [WeekUiState.Content.isCurrentWeek] and `HabitPeriod.todayIndex` are both computed against the
 * injected clock **when a value is produced**. Nothing re-emits at midnight, so an app left open
 * across a day boundary keeps yesterday's ring and keeps calling last week "this week" until
 * something upstream changes.
 *
 * That is not an oversight, it is `Plan.md` **M6.9**, which exists for exactly this. It is called
 * out here because the bug it describes is invisible in every test with a fixed clock — which is
 * every test in this file.
 *
 * ### A check-off says what it changed, once
 *
 * Every accepted write sends a [WeekMessage] down [messages], which `WeekScreen` turns into the
 * undo snackbar (M6.6). It is a `Channel` rather than a field on [WeekUiState] because it must be
 * said exactly once — `CLAUDE.md` §6's rule for one-shots, and the reason a rotation does not
 * re-announce a tap the user made a minute ago.
 *
 * ### ⚠ A refused write still says nothing
 *
 * `ToggleCompletion` returns `false` when the date has not happened yet, and [onEvent] sends no
 * message for it. That is not an oversight carried over: **M6.7 makes future pills untappable**, so
 * the refusal is about to stop being reachable through the UI at all, and inventing a sentence for
 * it now would design M6.7's screen a step early. The guard stays where it is either way — it is
 * the domain's rule, and the ViewModel must not route around it.
 *
 * **The window worth knowing about:** until M6.7 lands, a tap on a future pill does nothing and
 * says nothing. That is the whole of the gap.
 */
@HiltViewModel
class WeekViewModel
    @Inject
    constructor(
        private val getHabitsForWeek: GetHabitsForWeek,
        private val toggleCompletion: ToggleCompletion,
        // Injected rather than read, so "which week is this?" is a testable claim (`CLAUDE.md` §6).
        private val clock: Clock,
    ) : ViewModel() {
        /**
         * Which week is on screen, as its first day.
         *
         * **Always a week start, and it stays one without being re-checked.** Only two things can
         * put an arbitrary date in here — construction and [WeekEvent.ThisWeekPressed], both of
         * which start from "today" — and both go through [currentWeek], which normalises. Stepping
         * ±7 days from a week start lands on a week start, so paging cannot drift off the boundary.
         *
         * It matters because [WeekUiState.Content.weekStart] is what the `‹ Aug 17 – Aug 23 ›` label
         * is derived from, while the seven cells come from `GetHabitsForWeek`. Those two must agree,
         * and they agree by construction rather than by coincidence.
         */
        private val referenceWeek = MutableStateFlow(currentWeek())

        @OptIn(ExperimentalCoroutinesApi::class)
        val uiState: StateFlow<WeekUiState> =
            referenceWeek
                .flatMapLatest { week ->
                    getHabitsForWeek(week).map { habits -> stateFor(week, habits) }
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
                    // Not an empty Content, and not Empty: neither is known yet. Starting at either
                    // would flash a wrong screen on every cold start — Empty in particular reads as
                    // "you have no habits", which is a sentence no one should see while loading.
                    initialValue = WeekUiState.Loading,
                )

        /**
         * ⚠ **`CONFLATED`, so a fast hand does not build a queue of stale offers.**
         *
         * Checking off five habits in a row is the normal way this screen is used, not an edge
         * case. A buffered channel would hold all five and the screen would walk through them one
         * after another, each undo arriving seconds after the tap it belongs to and the last one
         * long after the user had moved on. Conflation keeps only the newest, which is the only one
         * whose sentence is still true on screen.
         *
         * What that gives up is real and worth stating: **the older taps lose their Undo button.**
         * They keep the better one — the pill itself, which is still right there and still flips
         * back. Losing a shortcut beats a backlog of sentences about things that happened a while
         * ago (`CLAUDE.md` §3.5's principle, applied to the screen rather than to sync).
         *
         * It also means a message that arrives while nothing is collecting is replaced rather than
         * stacked, so returning to the tab cannot dump a history at the user.
         */
        private val _messages = Channel<WeekMessage>(Channel.CONFLATED)

        /** The one-shot stream `WeekScreen` renders as the undo snackbar (`CLAUDE.md` §6). */
        val messages: Flow<WeekMessage> = _messages.receiveAsFlow()

        fun onEvent(event: WeekEvent) {
            when (event) {
                is WeekEvent.DayToggled -> {
                    // Resolved here, synchronously, rather than inside the coroutine: this is the
                    // habit as it was named when the finger landed. By the time the write returns,
                    // the state has already re-emitted.
                    val habitName = habitName(event.habitId)
                    viewModelScope.launch {
                        // The write lands in Room and returns; Notion finds out when the sync engine
                        // next runs (`CLAUDE.md` §1). The `false` means "that day has not happened
                        // yet" — no write, and so nothing to announce or offer back.
                        val written =
                            toggleCompletion(
                                habitId = event.habitId,
                                date = event.date,
                                completed = event.completed,
                            )
                        if (written && habitName != null) {
                            _messages.trySend(
                                WeekMessage(
                                    habitId = event.habitId,
                                    habitName = habitName,
                                    date = event.date,
                                    completed = event.completed,
                                    isToday = event.date == LocalDate.now(clock),
                                ),
                            )
                        }
                    }
                }

                is WeekEvent.UndoPressed -> {
                    viewModelScope.launch {
                        // ⚠ **No message.** Sending one here would offer to undo the undo, and the
                        // next press would offer to undo that — see `WeekEvent.UndoPressed`.
                        toggleCompletion(
                            habitId = event.habitId,
                            date = event.date,
                            completed = event.completed,
                        )
                    }
                }

                WeekEvent.PreviousWeekPressed -> {
                    stepWeeks(-1)
                }

                // Deliberately unbounded in both directions. Paging past the last habit's start date
                // shows a week of BEFORE_START cells and paging into the future shows FUTURE ones —
                // both are honest, both are cheap, and a clamp would need a rule the design does not
                // give (`docs/design-spec.md` §3 draws only `‹` and `›`).
                WeekEvent.NextWeekPressed -> {
                    stepWeeks(+1)
                }

                WeekEvent.ThisWeekPressed -> {
                    referenceWeek.value = currentWeek()
                }
            }
        }

        /**
         * Calendar arithmetic, not instant arithmetic.
         *
         * `LocalDate.plusDays` counts days on a calendar, so a week that contains a daylight-saving
         * change is still seven days long. Adding `7 * 24` hours to an instant would not be.
         */
        private fun stepWeeks(weeks: Long) {
            referenceWeek.value = referenceWeek.value.plusDays(weeks * DAYS_PER_WEEK)
        }

        private fun currentWeek(): LocalDate = startOfWeek(LocalDate.now(clock))

        /**
         * The name to put in a [WeekMessage], or `null` if there is nothing to name.
         *
         * ⚠ **`null` in practice means the tap came from a card that is not on screen**, which no
         * gesture can produce: the pill lives inside the very row this reads back. Returning null
         * rather than inventing a placeholder keeps the impossible case honest — the write still
         * happens, and what is skipped is only the sentence about it. Naming a habit "Habit" would
         * be worse than saying nothing, because the whole value of the message is the name.
         */
        private fun habitName(id: HabitId): String? =
            (uiState.value as? WeekUiState.Content)
                ?.habits
                ?.firstOrNull { period -> period.habit.id == id }
                ?.habit
                ?.name

        private fun stateFor(
            week: LocalDate,
            habits: List<HabitPeriod>,
        ): WeekUiState =
            if (habits.isEmpty()) {
                // Week-independent: habits are global, so an empty list here means there are no
                // active habits at all rather than none *this* week. `WeekUiState.Empty` explains
                // why that distinction matters and why it carries no week.
                WeekUiState.Empty
            } else {
                WeekUiState.Content(
                    weekStart = week,
                    habits = habits,
                    isCurrentWeek = week == currentWeek(),
                )
            }

        private companion object {
            /** `CLAUDE.md` §6's `WhileSubscribed(5_000)` — survives a rotation, stops on a real exit. */
            const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
        }
    }
