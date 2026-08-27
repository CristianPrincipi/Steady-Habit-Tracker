package com.cristianprincipi.habits.feature.week

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cristianprincipi.habits.core.datetime.DAYS_PER_WEEK
import com.cristianprincipi.habits.core.datetime.startOfWeek
import com.cristianprincipi.habits.domain.model.HabitPeriod
import com.cristianprincipi.habits.domain.usecase.GetHabitsForWeek
import com.cristianprincipi.habits.domain.usecase.ToggleCompletion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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
 * decides what each of the seven cells means. This class picks a week, labels the result, and
 * forwards four gestures.
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
 * ### ⚠ A refused write is dropped on the floor, for now
 *
 * `ToggleCompletion` returns `false` when the date has not happened yet, and [onEvent] ignores it.
 * M6.7 makes future pills untappable and M6.6 owns the snackbar, so declaring a one-shot `Channel`
 * here — with a single message nothing yet sends and nothing yet shows — would be inventing their
 * design a milestone early (`CLAUDE.md` §6 wants deferred work in `Plan.md`, and both already are).
 *
 * **The window worth knowing about:** once M6.5 wires the tap and before M6.7 lands, a tap on a
 * future pill does nothing visible. That is the whole of the gap.
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

        fun onEvent(event: WeekEvent) {
            when (event) {
                is WeekEvent.DayToggled -> {
                    viewModelScope.launch {
                        // The write lands in Room and returns; Notion finds out when the sync engine
                        // next runs (`CLAUDE.md` §1). The `false` this can return means "that day has
                        // not happened yet" — see the class KDoc for why it goes nowhere yet.
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
