package com.cristianprincipi.habits.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cristianprincipi.habits.core.datetime.TodayFlow
import com.cristianprincipi.habits.core.ui.HabitsEmptyReason
import com.cristianprincipi.habits.core.ui.observeEmptyReason
import com.cristianprincipi.habits.domain.model.HabitPeriod
import com.cristianprincipi.habits.domain.repository.SyncStatusRepository
import com.cristianprincipi.habits.domain.usecase.GetHabitsForMonth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/**
 * The Month view's state — `docs/design-spec.md` §2.
 *
 * Deliberately the same shape as `WeekViewModel`, because it is the same problem one window wider:
 * a pinned position that may be null, a day that moves on its own, and a query that has to follow
 * both. Where it differs, it differs for a reason named at the field.
 *
 * @param today the day, as a `Flow`, so a month left open past midnight is not stale. It matters
 *   less here than on Week — a rollover changes the *month* only twelve times a year — but it also
 *   changes which cells are `FUTURE` rather than `INCOMPLETE`, which is every night.
 */
@HiltViewModel
class MonthViewModel
    @Inject
    constructor(
        private val getHabitsForMonth: GetHabitsForMonth,
        private val syncStatus: SyncStatusRepository,
        private val today: TodayFlow,
        private val clock: Clock,
    ) : ViewModel() {
        /**
         * The month the user paged to, or `null` for "follow today".
         *
         * ⚠ **`null` is not "this month", it is "whatever month it is".** Same reasoning as
         * `WeekViewModel.anchor`: storing the current month would make a screen left open across a
         * month boundary keep showing the old one, and there is nothing to notice it and correct it.
         */
        private val anchor = MutableStateFlow<YearMonth?>(null)

        private data class Snapshot(
            val month: YearMonth,
            val today: LocalDate,
        )

        private val snapshot: Flow<Snapshot> =
            combine(anchor, today()) { pinned, today ->
                Snapshot(month = pinned ?: YearMonth.from(today), today = today)
            }

        /**
         * The habits for the visible month, carried **with** the snapshot that asked for them.
         *
         * Pairing them is what stops the header naming one month while the grid draws another: by
         * the time a query returns the user may have paged on, and a snapshot read back afterwards
         * would be the newer one.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        private val visibleMonth: Flow<Pair<Snapshot, List<HabitPeriod>>> =
            snapshot.flatMapLatest { visible ->
                getHabitsForMonth(visible.month).map { habits -> visible to habits }
            }

        val uiState: StateFlow<MonthUiState> =
            combine(visibleMonth, syncStatus.observeEmptyReason()) { (visible, habits), reason ->
                stateFor(visible, habits, reason)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
                // The month is known without asking anyone, so Loading still has a title.
                initialValue = MonthUiState.Loading(currentMonth()),
            )

        fun onEvent(event: MonthEvent) {
            when (event) {
                MonthEvent.PreviousMonthPressed -> {
                    moveMonths(-1)
                }

                MonthEvent.NextMonthPressed -> {
                    moveMonths(+1)
                }

                MonthEvent.ThisMonthPressed -> {
                    anchor.value = null
                }
            }
        }

        /**
         * Steps the anchor, and **normalises back to `null` on the current month**.
         *
         * Paging away and back looks identical to never having paged, so it must behave identically
         * at the next month boundary — otherwise two states that render the same diverge later.
         * `YearMonth.plusMonths` rolls the year by itself, which is the whole of
         * `docs/design-spec.md` §2's "rolling the year at the boundaries".
         */
        private fun moveMonths(months: Long) {
            val current = currentMonth()
            val moved = (anchor.value ?: current).plusMonths(months)
            anchor.value = moved.takeIf { it != current }
        }

        private fun currentMonth(): YearMonth = YearMonth.from(LocalDate.now(clock))

        private fun stateFor(
            visible: Snapshot,
            habits: List<HabitPeriod>,
            reason: HabitsEmptyReason,
        ): MonthUiState =
            if (habits.isEmpty()) {
                // No habits at all rather than none this month — see MonthUiState.Empty.
                MonthUiState.Empty(month = visible.month, reason = reason)
            } else {
                MonthUiState.Content(
                    month = visible.month,
                    habits = habits,
                    // Against the snapshot's own day, not a second reading of the clock: a value
                    // built from one answer to "what day is it" must not contain a later one.
                    isCurrentMonth = visible.month == YearMonth.from(visible.today),
                )
            }

        private companion object {
            const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
        }
    }
