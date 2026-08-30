package com.cristianprincipi.habits.core.designsystem.component

import com.cristianprincipi.habits.data.local.mapper.splitEmojiAndName
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The other end of M7.2's split.
 *
 * `HabitMapper.splitEmojiAndName` takes a Notion property name apart and `HabitMapperTest` covers it
 * thoroughly — including the asymmetry that only `✒️ Journal` carries a `U+FE0F` variation selector.
 * What nothing covered is putting the halves **back together**, which is what a chip label and the
 * glyph in a habit's circle both do.
 *
 * ⚠ **The case that matters is the one the real database does not contain.** All five of the user's
 * habits carry an emoji (`docs/notion-schema.md` §2.3), and so does every preview, gallery entry and
 * fixture in this repo — so an emoji-less habit looks right in every screenshot anyone has ever
 * taken of this app, and is wrong the moment a checkbox column is called plainly `Meditate`.
 */
class HabitDisplayTest {
    // --- the one-line label ----------------------------------------------------------------------

    @Test
    fun anEmojiAndANameAreJoinedByOneSpace() {
        assertEquals("📖 Read", habitLabel("📖", "Read"))
    }

    @Test
    fun aHabitWithNoEmojiIsNotIndentedByThePlaceWhereOneWouldGo() {
        // ⭐ The defect this was written to catch: `"$emoji $name"` renders " Meditate", one space
        // out of line with every neighbour that has an icon. Nothing in the user's own database
        // would ever have shown it.
        assertEquals("Meditate", habitLabel("", "Meditate"))
    }

    @Test
    fun theVariationSelectorSurvivesTheJoin() {
        // `✒️` is U+2712 plus U+FE0F. The join must not drop, add or reorder those bytes — the same
        // rule the split half is held to, applied to the direction that puts them back together.
        val label = habitLabel("✒️", "Journal")

        assertEquals("✒️ Journal", label)
        assertEquals(listOf(0x2712, 0xFE0F, ' '.code), label.take(3).map(Char::code))
    }

    @Test
    fun aSplitNameRoundTripsBackToTheStringItCameFrom() {
        // ⭐⭐ M7.2's actual wording — "round-trip the exact original string". Nothing in the app
        // sends a name back to Notion (it writes by property id, ADR-038), so this is the only
        // round-trip there is: split for display, rejoin for display, get the original back.
        //
        // ⚠ It holds for these and is **not** a general guarantee: the split trims the tail, so a
        // property named "📖  Read" with two spaces comes back with one. That is a display string
        // and no API call depends on it — but a future caller tempted to send this upstream needs to
        // know it is lossy, and this is where that is written down.
        //
        // ⚠ **The real split, imported rather than mirrored.** Both halves are `internal`, which in
        // Kotlin is module-wide, so this calls the production function. Re-implementing the rule
        // here would let the two drift and leave this test agreeing with a copy of itself.
        listOf("📖 Read", "✒️ Journal", "📜 Quote", "🥕 Diet", "🎯 Focus", "Meditate")
            .forEach { original ->
                val (emoji, name) = splitEmojiAndName(original)
                assertEquals(original, habitLabel(emoji, name))
            }
    }

    // --- the glyph in the circle (M7.2, the user's call) -------------------------------------------

    @Test
    fun anEmojiIsTheGlyphWhenThereIsOne() {
        assertEquals("📖", habitGlyph("📖", "Read"))
    }

    @Test
    fun aHabitWithNoEmojiFallsBackToItsInitial() {
        // ⭐ Without this the circle draws empty, and ADR-027 already ruled once that a
        // drawn-but-blank element reads as something that failed to load rather than as quiet.
        assertEquals("M", habitGlyph("", "Meditate"))
    }

    @Test
    fun theInitialIsUppercasedRegardlessOfHowTheColumnWasTyped() {
        assertEquals("M", habitGlyph("", "meditate"))
    }

    @Test
    fun theInitialIsTakenByCodePointRatherThanByFirstChar() {
        // ⚠ `first()` on an astral character hands back half a surrogate pair, which renders as a
        // replacement box. This is reachable rather than theoretical: an emoji with no space after
        // it stays in the NAME half (`anEmojiWithNoNameAfterItIsLeftAlone`), so the fallback can be
        // handed one.
        assertEquals("🥕", habitGlyph("", "🥕"))
    }

    @Test
    fun aNameOfNothingButSpaceLeavesThePlainCircle() {
        // Not reachable from the user's schema, but the alternative is an index out of bounds.
        assertEquals("", habitGlyph("", "   "))
    }
}
