package com.cristianprincipi.habits.core.designsystem.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards the ported type scale against `docs/tokens.md §2`, for the same reason [ColorTest] guards
 * the palette: a wrong size or weight compiles, lints and renders, and looks approximately right.
 *
 * What this cannot check is whether the bundled font files actually resolve on a device — a
 * `FontFamily` is only a descriptor until something measures text with it. `TypographyRenderTest`
 * in `androidTest` covers that.
 */
class TypeTest {
    @Test
    fun `the material scale carries the sizes from tokens md`() {
        assertEquals(30.sp, HabitsTypography.displaySmall.fontSize)
        assertEquals(24.sp, HabitsTypography.headlineSmall.fontSize)
        assertEquals(20.sp, HabitsTypography.titleLarge.fontSize)
        assertEquals(16.sp, HabitsTypography.titleMedium.fontSize)
        assertEquals(14.sp, HabitsTypography.titleSmall.fontSize)
        assertEquals(12.sp, HabitsTypography.bodySmall.fontSize)
        assertEquals(10.sp, HabitsTypography.labelSmall.fontSize)
    }

    @Test
    fun `the material scale carries the weights from tokens md`() {
        // §2.2: medium 500 exists but only for the nav label and weekday initials, both of which
        // are extended styles. Everything Material reaches for is 400, 600 or 700.
        assertEquals(FontWeight.Bold, HabitsTypography.headlineSmall.fontWeight)
        assertEquals(FontWeight.SemiBold, HabitsTypography.titleSmall.fontWeight)
        assertEquals(FontWeight.SemiBold, HabitsTypography.labelLarge.fontWeight)
        assertEquals(FontWeight.Normal, HabitsTypography.bodyMedium.fontWeight)
    }

    @Test
    fun `line heights follow the tailwind scale the prototype uses`() {
        assertEquals(16.sp, HabitsTypography.bodySmall.lineHeight)
        assertEquals(20.sp, HabitsTypography.bodyMedium.lineHeight)
        assertEquals(24.sp, HabitsTypography.bodyLarge.lineHeight)
        assertEquals(28.sp, HabitsTypography.titleLarge.lineHeight)
        assertEquals(32.sp, HabitsTypography.headlineSmall.lineHeight)
        assertEquals(36.sp, HabitsTypography.displaySmall.lineHeight)
    }

    @Test
    fun `no material slot is left at its roboto default`() {
        // A slot left unset is not "unused" — it is a stock Material component rendering in the
        // wrong typeface the first time anyone uses one.
        val slots =
            listOf(
                HabitsTypography.displayLarge,
                HabitsTypography.displayMedium,
                HabitsTypography.displaySmall,
                HabitsTypography.headlineLarge,
                HabitsTypography.headlineMedium,
                HabitsTypography.headlineSmall,
                HabitsTypography.titleLarge,
                HabitsTypography.titleMedium,
                HabitsTypography.titleSmall,
                HabitsTypography.bodyLarge,
                HabitsTypography.bodyMedium,
                HabitsTypography.bodySmall,
                HabitsTypography.labelLarge,
                HabitsTypography.labelMedium,
                HabitsTypography.labelSmall,
            )

        assertEquals(EVERY_MATERIAL_SLOT, slots.size)
        slots.forEach { style ->
            assertNotEquals(null, style.fontFamily)
            assertNotEquals(TextStyle.Default.fontSize, style.fontSize)
        }
    }

    @Test
    fun `the screen title is tracked tight and the eyebrows widest`() {
        assertEquals((-0.025).em, HabitsTypography.headlineSmall.letterSpacing)
        assertEquals(0.1.em, HabitsDarkTextStyles.eyebrow.letterSpacing)
        assertEquals(0.1.em, HabitsDarkTextStyles.eyebrowSmall.letterSpacing)
        assertEquals(0.025.em, HabitsDarkTextStyles.weekdayInitial.letterSpacing)
    }

    @Test
    fun `the extended styles carry the sizes and weights the design asks for`() {
        assertEquals(10.sp, HabitsDarkTextStyles.eyebrow.fontSize)
        assertEquals(FontWeight.SemiBold, HabitsDarkTextStyles.eyebrow.fontWeight)
        assertEquals(9.sp, HabitsDarkTextStyles.eyebrowSmall.fontSize)
        assertEquals(10.sp, HabitsDarkTextStyles.weekdayInitial.fontSize)
        assertEquals(FontWeight.Medium, HabitsDarkTextStyles.weekdayInitial.fontWeight)
        assertEquals(30.sp, HabitsDarkTextStyles.statValue.fontSize)
        assertEquals(24.sp, HabitsDarkTextStyles.metricValue.fontSize)
    }

    @Test
    fun `the numeric styles request tabular figures`() {
        // Requested, and currently inert: DM Sans 4.004 ships no `tnum` feature — see the note on
        // HabitsDarkTextStyles. This pins the intent so the request is not quietly dropped.
        assertEquals("tnum", HabitsDarkTextStyles.statValue.fontFeatureSettings)
        assertEquals("tnum", HabitsDarkTextStyles.metricValue.fontFeatureSettings)
        assertNull(HabitsTypography.bodyMedium.fontFeatureSettings)
    }

    @Test
    fun `the mono styles are a different family from the sans styles`() {
        assertNotEquals(HabitsTypography.bodyMedium.fontFamily, HabitsDarkTextStyles.mono.fontFamily)
        assertEquals(HabitsDarkTextStyles.mono.fontFamily, HabitsDarkTextStyles.monoSmall.fontFamily)
        assertEquals(14.sp, HabitsDarkTextStyles.mono.fontSize)
        assertEquals(10.sp, HabitsDarkTextStyles.monoSmall.fontSize)
    }

    @Test
    fun `optical size tracks font size, so styles of different sizes get different families`() {
        // DM Sans is variable and its `opsz` axis defaults to 9, the low end. Each style asks for
        // the optical size matching its own font size, which is what the browser does by default —
        // so two styles at the same size share a family, and two at different sizes must not.
        assertEquals(HabitsTypography.bodyMedium.fontFamily, HabitsTypography.titleSmall.fontFamily)
        assertEquals(HabitsTypography.bodyMedium.fontFamily, HabitsTypography.labelLarge.fontFamily)
        assertNotEquals(HabitsTypography.bodySmall.fontFamily, HabitsTypography.bodyLarge.fontFamily)
        assertNotEquals(HabitsTypography.titleLarge.fontFamily, HabitsTypography.displaySmall.fontFamily)
    }

    private companion object {
        const val EVERY_MATERIAL_SLOT = 15
    }
}
