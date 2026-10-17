package com.cristianprincipi.habits.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [ReadingInput] — what a typed number is, and the narrow ways it is allowed to be written. */
class ReadingInputTest {
    @Test
    fun aDotAndACommaAreTheSameDecimal() {
        // ADR-156 §2: a decimal keyboard offers whichever separator its locale uses.
        assertEquals(7.5, ReadingInput.parse("7.5"))
        assertEquals(7.5, ReadingInput.parse("7,5"))
    }

    @Test
    fun wholeNumbersAndTrailingSeparatorsAreNumbers() {
        assertEquals(84.0, ReadingInput.parse("84"))
        assertEquals(84.0, ReadingInput.parse("84."))
        assertEquals(84.0, ReadingInput.parse("84,"))
        assertEquals(0.5, ReadingInput.parse(".5"))
    }

    @Test
    fun surroundingSpacesAreIgnored() {
        assertEquals(82.9, ReadingInput.parse("  82.9 "))
    }

    @Test
    fun aNegativeNumberIsANumber() {
        // Nothing here knows what the column measures (`CLAUDE.md` §3.4); Notion allows it.
        assertEquals(-1.5, ReadingInput.parse("-1,5"))
    }

    @Test
    fun twoSeparatorsAreRefusedRatherThanGuessed() {
        // `1.234,5` is a grouping in one locale and nonsense in another; guessing could store a
        // weight a thousand times too large.
        assertNull(ReadingInput.parse("1.234,5"))
        assertNull(ReadingInput.parse("1,234.5"))
        assertNull(ReadingInput.parse("7.5.1"))
    }

    @Test
    fun hoursAndMinutesAreRefused() {
        // Decimal hours only — the user's ruling, 2026-10-16. `7:30` is not guessed at.
        assertNull(ReadingInput.parse("7:30"))
        assertNull(ReadingInput.parse("7h30"))
    }

    @Test
    fun nothingThatIsNotAPlainNumberIsAccepted() {
        listOf("", " ", "-", ".", ",", "abc", "7.5kg", "1e3", "NaN", "Infinity", "+5", "--5").forEach { text ->
            assertNull("'$text' should not be a number", ReadingInput.parse(text))
        }
    }
}
