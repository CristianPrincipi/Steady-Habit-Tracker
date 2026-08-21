package com.cristianprincipi.habits.core.designsystem.icon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the generated glyph data against `docs/tokens.md §5`.
 *
 * The path strings themselves are not asserted — restating 30 SVG paths in a test would only prove
 * they were copied twice. What is worth pinning is the shape of the set: thirteen glyphs, all
 * present, none empty, none accidentally sharing a definition after a generator run.
 */
class LucideGlyphsTest {
    @Test
    fun `there are thirteen glyphs and no more`() {
        assertEquals(THIRTEEN, Lucide.all.size)
        assertEquals(
            THIRTEEN,
            Lucide.all
                .map { it.name }
                .distinct()
                .size,
        )
    }

    @Test
    fun `every glyph carries path data`() {
        Lucide.all.forEach { glyph ->
            assertTrue("${glyph.name} has no paths", glyph.pathData.isNotEmpty())
            glyph.pathData.forEach { data ->
                assertTrue("${glyph.name} has an empty path", data.isNotBlank())
                // Every SVG path starts with a move. A conversion that dropped its opening command
                // would still be a non-blank string.
                assertTrue("${glyph.name} path does not start with a move: $data", data.first() in "Mm")
            }
        }
    }

    @Test
    fun `no two glyphs share the same geometry`() {
        // A generator that mapped two names onto one SVG would be invisible until someone noticed
        // the Year tab and the Stats tab wearing the same icon.
        assertEquals(
            THIRTEEN,
            Lucide.all
                .map { it.pathData }
                .distinct()
                .size,
        )
    }

    @Test
    fun `the two renamed glyphs kept their lucide names, not the prototype's`() {
        // docs/tokens.md §5 records `align-justify` and `bar-chart-2`, which lucide-react exported
        // when the prototype was built. Both were renamed upstream; these are the current names, and
        // Lucide's own alias metadata confirms the geometry is unchanged.
        assertEquals("text-align-justify", Lucide.TextAlignJustify.name)
        assertEquals("chart-no-axes-column", Lucide.ChartNoAxesColumn.name)
    }

    @Test
    fun `the nav strokes differ, which is the whole reason these are built in kotlin`() {
        assertTrue(LucideStroke.NAV_ACTIVE > LucideStroke.NAV_INACTIVE)
        assertEquals(LUCIDE_DEFAULT, LucideStroke.DEFAULT, TOLERANCE)
        assertEquals(NAV_ACTIVE, LucideStroke.NAV_ACTIVE, TOLERANCE)
        assertEquals(NAV_INACTIVE, LucideStroke.NAV_INACTIVE, TOLERANCE)
        assertEquals(CHECK, LucideStroke.CHECK, TOLERANCE)
    }

    private companion object {
        const val THIRTEEN = 13
        const val TOLERANCE = 0.001f
        const val LUCIDE_DEFAULT = 2f
        const val NAV_ACTIVE = 2.2f
        const val NAV_INACTIVE = 1.5f
        const val CHECK = 3f
    }
}
