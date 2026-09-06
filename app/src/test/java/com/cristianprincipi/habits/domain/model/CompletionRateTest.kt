package com.cristianprincipi.habits.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The two invariants [CompletionRate] refuses to be constructed without — [StreakTest]'s subject,
 * for the other half of the same stat tile.
 *
 * ### ⭐⭐ Why this file exists: the measurement found it and the reading did not
 *
 * M13.8's audit read `domain/` and concluded every model with logic was covered. Kover disagreed:
 * `CompletionRate` was the one class in `domain/model` below 100%, and the uncovered line was the
 * **message of the second `require`** — a line that only runs when the guard actually fires. Nothing
 * in the suite constructs a `CompletionRate` directly at all; every use goes through
 * `GetCompletionRate`, which cannot violate the invariant by construction. **So both guards were
 * documented and neither was ever proved to fire**, which is the same defect
 * `aStreakThatIsCurrentlyTheBestIsAllowed` exists to rule out one file over.
 *
 * The invariants matter for `Streak`'s reason: a wrong rate is not a crash but a believable
 * percentage in a stat tile that nobody questions. Failing at construction turns a silent display
 * bug into a loud one.
 */
class CompletionRateTest {
    @Test
    fun aWindowCannotHaveNegativeDays() {
        assertThrows(IllegalArgumentException::class.java) {
            CompletionRate(completedDays = 0, eligibleDays = -1)
        }
    }

    @Test
    fun aHabitCannotBeKeptMoreOftenThanTheWindowAllows() {
        // The failure this catches: a completion counted from before the habit's start date, or the
        // same day counted twice — both of which produce a rate above 100% rather than an error.
        assertThrows(IllegalArgumentException::class.java) {
            CompletionRate(completedDays = 8, eligibleDays = 7)
        }
    }

    @Test
    fun completionsCannotBeNegative() {
        assertThrows(IllegalArgumentException::class.java) {
            CompletionRate(completedDays = -1, eligibleDays = 7)
        }
    }

    /**
     * The boundary the guard must **not** reject, so that the two assertions above are failing on
     * the rule rather than on any construction at all.
     */
    @Test
    fun aPerfectWindowIsAllowedAndIsOneHundredPercent() {
        val everyDayKept = CompletionRate(completedDays = 7, eligibleDays = 7)

        assertEquals(100, everyDayKept.percent)
    }
}
