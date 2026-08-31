package com.cristianprincipi.habits.data.local.mapper

import com.cristianprincipi.habits.data.local.entity.HabitEntity
import com.cristianprincipi.habits.domain.model.HabitColor
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * The emoji split, against the five property names the user's database actually has.
 *
 * These are not invented examples. They come from `docs/notion-schema.md` §2.3, which took them from
 * the raw bytes of a Notion export precisely because two of the five had been misread from a
 * screenshot.
 *
 * The names are written as the emoji themselves — Kotlin source is UTF-8 and the compiler is happy —
 * but [theEmojiAreTheCodePointsTheyLookLike] asserts their **code points** rather than trusting the
 * glyphs. An emoji that some editor, terminal or merge tool quietly rewrote still looks right on
 * screen; only its code points give it away.
 *
 * The interesting one is Journal. Its nib is `U+2712` **plus a `U+FE0F` variation selector**, and the
 * other four carry no selector — so a split that trimmed, normalised or filtered by character class
 * would change that property name and make every API call for it fail.
 */
class HabitMapperTest {
    @Test
    fun splitsTheFiveRealPropertyNames() {
        val cases =
            listOf(
                "📖 Read" to ("📖" to "Read"),
                "✒️ Journal" to ("✒️" to "Journal"),
                "📜 Quote" to ("📜" to "Quote"),
                "🥕 Diet" to ("🥕" to "Diet"),
                "🎯 Focus" to ("🎯" to "Focus"),
            )

        cases.forEach { (propertyName, expected) ->
            assertEquals(expected, splitEmojiAndName(propertyName))
        }
    }

    @Test
    fun theEmojiAreTheCodePointsTheyLookLike() {
        // Journal alone carries the U+FE0F variation selector. That asymmetry is real data, not a
        // typo to tidy up: a helper that added or removed selectors to make these uniform would
        // corrupt four names to fix nothing.
        assertEquals(listOf(0xD83D, 0xDCD6), codeUnitsOfEmojiIn("📖 Read"))
        assertEquals(listOf(0x2712, 0xFE0F), codeUnitsOfEmojiIn("✒️ Journal"))
        assertEquals(listOf(0xD83D, 0xDCDC), codeUnitsOfEmojiIn("📜 Quote"))
        assertEquals(listOf(0xD83E, 0xDD55), codeUnitsOfEmojiIn("🥕 Diet"))
        assertEquals(listOf(0xD83C, 0xDFAF), codeUnitsOfEmojiIn("🎯 Focus"))
    }

    @Test
    fun aNameWithNoEmojiKeepsAllOfItself() {
        assertEquals("" to "Read", splitEmojiAndName("Read"))
    }

    @Test
    fun aTwoWordNameIsNotMistakenForAnEmojiAndAName() {
        // The failure this catches: splitting on the first space unconditionally, which would file
        // this habit under an emoji of "Read" and a name of "more".
        assertEquals("" to "Read more", splitEmojiAndName("Read more"))
    }

    @Test
    fun anEmojiWithNoNameAfterItIsLeftAlone() {
        // Nothing sensible to show as a name, so the whole string stays the name rather than the
        // habit rendering as a blank label beside a circle.
        assertEquals("" to "📖 ", splitEmojiAndName("📖 "))
    }

    @Test
    fun mapsAnEntityOntoItsDomainModel() {
        val entity =
            HabitEntity(
                id = "local-1",
                notionPropertyId = "a%3Ab",
                notionPropertyName = "🥕 Diet",
                color = "ORANGE",
                startDateEpochDay = LocalDate.of(2026, 8, 19).toEpochDay(),
                isActive = true,
                sortOrder = 3,
            )

        val habit = entity.toDomain()

        assertEquals("local-1", habit.id.value)
        assertEquals("Diet", habit.name)
        assertEquals("🥕", habit.emoji)
        assertEquals(HabitColor.ORANGE, habit.color)
        assertEquals(LocalDate.of(2026, 8, 19), habit.startDate)
    }

    @Test
    fun anUnreadableColourFallsBackInsteadOfThrowing() {
        // A colour is decoration. Losing the enum value must not take the habit's history with it.
        val entity =
            HabitEntity(
                id = "local-2",
                notionPropertyId = "b%3Ac",
                notionPropertyName = "📜 Quote",
                color = "CHARTREUSE",
                startDateEpochDay = 0,
                isActive = true,
                sortOrder = 0,
            )

        assertEquals(HabitColor.BLUE, entity.toDomain().color)
    }

    private fun codeUnitsOfEmojiIn(propertyName: String): List<Int> =
        splitEmojiAndName(propertyName).first.map { it.code }
}
