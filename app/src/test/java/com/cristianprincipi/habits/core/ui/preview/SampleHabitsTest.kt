package com.cristianprincipi.habits.core.ui.preview

import com.cristianprincipi.habits.domain.model.HabitId
import com.cristianprincipi.habits.domain.usecase.CalculateStreak
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The preview data, held to the two things that make it useful: it is the user's real habit set,
 * and it is the same every time.
 *
 * Testing fixtures can look like ceremony. It is not, for these two reasons specifically — a
 * mangled emoji renders fine and only fails at the API boundary, and a fixture that reshuffles
 * itself makes every preview a moving target that no design change can be reviewed against.
 */
class SampleHabitsTest {
    private val reference = SampleHabits.referenceDate

    @Test
    fun theEmojiAreTheRealOnesDownToTheirCodePoints() {
        // Asserting the glyphs would prove nothing: two of these five were misidentified from a
        // screenshot once already, and both render near-identically at small sizes
        // (docs/notion-schema.md §2.3).
        val emoji = SampleHabits.habits(reference).associate { it.id.value to it.emoji }

        assertEquals(
            listOf(0x1F4D6),
            emoji
                .getValue("read")
                .codePoints()
                .toArray()
                .toList(),
        )
        assertEquals(
            listOf(0x1F4DC),
            emoji
                .getValue("quote")
                .codePoints()
                .toArray()
                .toList(),
        )
        assertEquals(
            listOf(0x1F955),
            emoji
                .getValue("diet")
                .codePoints()
                .toArray()
                .toList(),
        )
        assertEquals(
            listOf(0x1F3AF),
            emoji
                .getValue("focus")
                .codePoints()
                .toArray()
                .toList(),
        )
    }

    @Test
    fun onlyJournalCarriesTheVariationSelector() {
        // The asymmetry docs/notion-schema.md §2.3 is emphatic about. Any helper that "tidied"
        // these strings would strip this byte and make every API call for that property fail.
        val habits = SampleHabits.habits(reference).associateBy { it.id.value }

        assertEquals(
            listOf(0x2712, 0xFE0F),
            habits
                .getValue("journal")
                .emoji
                .codePoints()
                .toArray()
                .toList(),
        )
        assertEquals(1, habits.values.count { it.emoji.contains('️') })
    }

    @Test
    fun theNamesHaveTheirEmojiSplitOff() {
        val names = SampleHabits.habits(reference).map { it.name }

        assertEquals(listOf("Read", "Journal", "Quote", "Diet", "Focus"), names)
    }

    @Test
    fun theSampleNumberWeekShowsAValueADashAndAFutureDay() {
        // M16.1. The preview exists so the user can judge the row, and the row has three looks: a
        // number, the dash they ruled on, and an empty future cell. A sample week missing any one of
        // them would hide it from the only review it gets.
        val days = SampleHabits.measureWeeks().flatMap { week -> week.days }

        assertTrue(days.any { it.value != null })
        assertTrue(days.any { it.value == null && !it.isFuture })
        assertTrue(days.any { it.isFuture })
        assertEquals(SampleHabits.measureWeeks(), SampleHabits.measureWeeks())
    }

    @Test
    fun theSameReferenceDateGivesTheSameHistoryEveryTime() {
        // The whole point of the seeded generator. Random() here would make every preview a
        // different picture on every recomposition.
        assertEquals(SampleHabits.completions(reference), SampleHabits.completions(reference))
    }

    @Test
    fun eachHabitsHistoryIsIndependentOfTheOthers() {
        // The seed is derived from the habit id, so adding a sixth habit later cannot shift the
        // five that exist — otherwise every preview would change in a diff about something else.
        val fromAll = SampleHabits.completions(reference).filter { it.habitId == HabitId("read") }
        val (_, fromOne) = SampleHabits.habitWithHistory("read", reference)

        assertEquals(fromOne, fromAll)
    }

    @Test
    fun everyCompletionFallsInsideItsHabitsOwnLifetime() {
        val habits = SampleHabits.habits(reference).associateBy { it.id }

        SampleHabits.completions(reference).forEach { completion ->
            val habit = habits.getValue(completion.habitId)
            assertTrue(
                "${habit.name} has a completion on ${completion.date}, outside ${habit.startDate}..$reference",
                completion.date >= habit.startDate && completion.date <= reference,
            )
        }
    }

    @Test
    fun theHabitsHaveVisiblyDifferentDensities() {
        // Five identical densities would make the Month and Year grids look wrong in a way no
        // screenshot review would flag as a data problem.
        val counts = SampleHabits.completions(reference).groupingBy { it.habitId.value }.eachCount()

        assertTrue("Read should be kept more often than Diet", counts.getValue("read") > counts.getValue("diet"))
        assertEquals(5, counts.size)
    }

    @Test
    fun theStartDatesAreStaggeredSoPreviewsExerciseBeforeStart() {
        val habits = SampleHabits.habits(reference).associateBy { it.id.value }

        assertTrue(habits.getValue("focus").startDate > habits.getValue("read").startDate)
        assertTrue(habits.getValue("focus").startDate > reference.minusDays(365))
    }

    @Test
    fun theHistorySpansMoreThanTheYearWindow() {
        // So a Year grid preview is full at its left edge rather than fading into blank cells that
        // look like a rendering bug.
        val earliest = SampleHabits.habits(reference).minOf { it.startDate }

        assertTrue(earliest <= reference.minusDays(365))
    }

    @Test
    fun theUnbrokenRunReallyIsAStreak() {
        // A stat-tile preview reading "current streak: 0" would be the least useful thing it could
        // show, and the generated history will not reliably contain a live run.
        val clock = Clock.fixed(reference.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        val run = SampleHabits.unbrokenRun(days = 12, reference = reference)

        val streak = CalculateStreak(clock)(run.map { it.date })

        assertEquals(12, streak.current)
        assertEquals(12, streak.best)
    }

    @Test
    fun nothingHereReadsAClock() {
        // A fixture that asked the system what day it was would produce different previews next
        // year, and a screenshot test that passed today and failed in January.
        val lastYear = LocalDate.of(2025, 3, 4)

        val habits = SampleHabits.habits(lastYear)

        assertEquals(lastYear.minusDays(95), habits.first { it.id.value == "focus" }.startDate)
    }
}
