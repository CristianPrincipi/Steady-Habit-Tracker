package com.cristianprincipi.habits.core.ui

import com.cristianprincipi.habits.core.designsystem.component.DayStatus
import com.cristianprincipi.habits.domain.model.DayState
import com.cristianprincipi.habits.domain.model.PeriodCell
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * The join between the domain's four day states and the design system's five.
 *
 * Two enums are kept apart on purpose — `domain/` carries no Compose, the design system carries no
 * domain — and the cost of that is a hand-written mapping, which is the kind of code that goes
 * wrong by pairing two cases the wrong way round and still compiling.
 *
 * ⚠ `HabitColor.resolve()` is **not** covered here: it is `@Composable`, so it needs a composition
 * to read the theme. `ThemeTest` in `androidTest` is where the palette itself is asserted.
 */
class HabitPresentationTest {
    private fun day(state: DayState) = PeriodCell.Day(LocalDate.of(2026, 8, 24), state)

    @Test
    fun everyDomainStateMapsToItsOwnCounterpart() {
        assertEquals(DayStatus.COMPLETED, day(DayState.COMPLETED).toDayStatus())
        assertEquals(DayStatus.INCOMPLETE, day(DayState.INCOMPLETE).toDayStatus())
        assertEquals(DayStatus.FUTURE, day(DayState.FUTURE).toDayStatus())
        assertEquals(DayStatus.BEFORE_START, day(DayState.BEFORE_START).toDayStatus())
    }

    @Test
    fun paddingIsTheOneStatusWithNoDomainState() {
        assertEquals(DayStatus.PADDING, PeriodCell.Padding.toDayStatus())
    }

    @Test
    fun theMappingIsOntoAndNothingCollapses() {
        // ⭐ The assertion that catches a copy-paste. Mapping two domain states to the same status
        // would draw a missed day and a future one identically — the exact distinction
        // `DayState.FUTURE`'s KDoc says has to survive — and every individual assertion above would
        // still pass if, say, BEFORE_START were wired to FUTURE.
        val mapped = DayState.entries.map { state -> day(state).toDayStatus() }

        assertEquals(DayState.entries.size, mapped.toSet().size)
    }

    @Test
    fun everyDayStatusExceptPaddingIsReachableFromTheDomain() {
        // The other direction: a `DayStatus` nothing can produce is either dead or a missed case.
        val reachable = DayState.entries.map { state -> day(state).toDayStatus() }.toSet()

        assertEquals(DayStatus.entries.toSet() - DayStatus.PADDING, reachable)
    }
}
