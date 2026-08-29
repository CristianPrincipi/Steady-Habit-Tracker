package com.cristianprincipi.habits.core.testing

import com.cristianprincipi.habits.core.datetime.TodayFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

/**
 * A [TodayFlow] whose midnight a test decides.
 *
 * The real one waits on a `delay` computed from the wall clock, which would make every rollover test
 * an exercise in virtual-time bookkeeping to assert something that has nothing to do with waiting.
 * `MidnightTodayFlowTest` owns the waiting; this owns the consequences.
 *
 * ⚠ **Move the clock with it.** In production these two cannot disagree — the ticker reads the same
 * clock it reports on — so a test that advanced only one of them would be asserting against a state
 * the app cannot reach. `WeekViewModelTest.rollOverTo` is the one place that does both.
 */
class FakeTodayFlow(
    initial: LocalDate,
) : TodayFlow {
    private val dates = MutableStateFlow(initial)

    override fun invoke(): Flow<LocalDate> = dates

    fun setTo(date: LocalDate) {
        dates.value = date
    }
}
