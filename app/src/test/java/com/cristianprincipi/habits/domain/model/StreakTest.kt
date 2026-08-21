package com.cristianprincipi.habits.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The two invariants [Streak] refuses to be constructed without.
 *
 * They exist because a wrong streak is not a crash — it is a believable pair of numbers in a stat
 * tile that nobody questions. Failing at construction turns a silent display bug into a loud one.
 */
class StreakTest {
    @Test
    fun aStreakCannotBeNegative() {
        assertThrows(IllegalArgumentException::class.java) { Streak(current = -1, best = 0) }
    }

    @Test
    fun bestCannotBeShorterThanCurrent() {
        // The failure this catches: a calculation that tracks the running streak but forgets to
        // raise the best one past it.
        assertThrows(IllegalArgumentException::class.java) { Streak(current = 9, best = 8) }
    }

    @Test
    fun aStreakThatIsCurrentlyTheBestIsAllowed() {
        val onItsBestDay = Streak(current = 12, best = 12)

        assertEquals(12, onItsBestDay.current)
        assertEquals(12, onItsBestDay.best)
    }

    @Test
    fun noneIsZeroOnBothCounts() {
        assertEquals(0, Streak.None.current)
        assertEquals(0, Streak.None.best)
    }
}
