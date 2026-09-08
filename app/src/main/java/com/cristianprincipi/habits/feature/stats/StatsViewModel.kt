package com.cristianprincipi.habits.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cristianprincipi.habits.core.datetime.TodayFlow
import com.cristianprincipi.habits.core.ui.observeEmptyReason
import com.cristianprincipi.habits.domain.model.Habit
import com.cristianprincipi.habits.domain.model.HabitId
import com.cristianprincipi.habits.domain.model.MeasureAverage
import com.cristianprincipi.habits.domain.repository.SyncStatusRepository
import com.cristianprincipi.habits.domain.usecase.GetHabitStats
import com.cristianprincipi.habits.domain.usecase.GetHabits
import com.cristianprincipi.habits.domain.usecase.GetMeasureAverages
import com.cristianprincipi.habits.domain.usecase.HabitStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The Stats screen's state — `docs/design-spec.md` §5.
 *
 * ### ⭐ The selection is an **id**, and it is allowed to point at nothing
 *
 * [selection] holds what the user tapped, not the habit it resolved to, and it is never written
 * back to. That matters when a habit leaves the mapped Notion set: hiding is silent and global
 * (ADR-081), so the chosen pill can simply stop existing between two emissions. Re-resolving on
 * every emission means the screen falls back to the first habit at that moment — and returns to
 * the user's choice by itself if the property comes back, which ADR-081 says it can.
 *
 * A `MutableStateFlow<Habit>` would instead keep drawing a habit the rest of the app has hidden,
 * with figures that never update again because nothing is querying for it.
 *
 * ### Why [today] is here on a screen with no calendar on it
 *
 * [GetHabitStats] reads the clock **once**, at invoke, and both figures it produces are measured
 * against that day: the current streak counts back from today, and the completion rate's window
 * ends there. So a Stats screen left open past midnight would keep yesterday's streak — and this is
 * a tab a user can sit on, unlike Week where the ring moving is obvious. Same reasoning as
 * `YearViewModel`, and the same fix: a new day re-invokes the use case.
 */
@HiltViewModel
class StatsViewModel
    @Inject
    constructor(
        private val getHabits: GetHabits,
        private val getHabitStats: GetHabitStats,
        private val getMeasureAverages: GetMeasureAverages,
        private val syncStatus: SyncStatusRepository,
        private val today: TodayFlow,
    ) : ViewModel() {
        /** The habit the user tapped, or `null` for "whichever is first" — see the class KDoc. */
        private val selection = MutableStateFlow<HabitId?>(null)

        /**
         * The habit set paired with the one to show, re-resolved on every emission.
         *
         * `firstOrNull()` is the design's own default (`§5.1`: "defaults to `habits[0]`"), and it
         * doubles as the fallback for a selection that no longer resolves.
         */
        private val chosen: Flow<Pair<List<Habit>, Habit?>> =
            combine(getHabits(), selection, today()) { habits, chosenId, _ ->
                habits to (habits.firstOrNull { it.id == chosenId } ?: habits.firstOrNull())
            }

        /**
         * The chosen habit's figures, or `null` when there is no habit to choose.
         *
         * `flatMapLatest` rather than `combine`: the query depends on *which* habit, so a new
         * selection has to cancel the old subscription rather than run beside it. It also re-runs
         * on a new day, which is the whole reason [today] is in [chosen].
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        private val statsForChosen: Flow<Pair<List<Habit>, HabitStats?>> =
            chosen.flatMapLatest { (habits, habit) ->
                if (habit == null) {
                    flowOf(habits to null)
                } else {
                    getHabitStats(habit).map { stats -> habits to stats }
                }
            }

        /**
         * The measure averages, re-read on a new day — M15.4.
         *
         * ⚠ [today] for [chosen]'s reason and one of its own: [GetMeasureAverages] reads the clock
         * **once, at invoke**, and its 30-day window ends on that day. A Stats screen left open past
         * midnight would otherwise keep averaging up to yesterday, silently, on the one tab a user
         * can sit on.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        private val averages: Flow<List<MeasureAverage>> =
            today().flatMapLatest { getMeasureAverages() }

        val uiState: StateFlow<StatsUiState> =
            combine(
                statsForChosen,
                averages,
                syncStatus.observeEmptyReason(),
            ) { (habits, stats), measureAverages, reason ->
                if (stats == null) {
                    // ⚠ No habits at all rather than no data for one — see StatsUiState.Empty. A
                    // user with measures but no habits still lands here, deliberately: the screen is
                    // a per-habit deep dive with a section appended, not the other way round, and
                    // two averages under a habit selector with nothing to select would be a screen
                    // about nothing.
                    StatsUiState.Empty(reason)
                } else {
                    StatsUiState.Content(
                        habits = habits,
                        selected = stats,
                        averages = measureAverages,
                    )
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
                initialValue = StatsUiState.Loading,
            )

        fun onEvent(event: StatsEvent) {
            when (event) {
                is StatsEvent.HabitSelected -> {
                    selection.value = event.habitId
                }
            }
        }

        private companion object {
            const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
        }
    }
