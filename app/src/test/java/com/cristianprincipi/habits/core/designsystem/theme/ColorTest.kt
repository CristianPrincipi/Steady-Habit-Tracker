package com.cristianprincipi.habits.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Guards the ported colour tokens against `docs/tokens.md §1`.
 *
 * A mistyped hex digit is invisible to every other gate this project has: it compiles, it lints, it
 * renders, and it looks approximately right in a screenshot. These assertions restate the extracted
 * values independently, so a typo or a well-meaning "tidy-up" of the palette fails here instead of
 * reaching the user's eye.
 *
 * The literals below are deliberately duplicated from the docs rather than read from `Color.kt` —
 * a test that derives its expectations from the code under test proves nothing.
 */
class ColorTest {
    @Test
    fun `dark scheme surfaces carry the tokens from tokens md`() {
        assertEquals(Color(0xFF111113), HabitsDarkColorScheme.background)
        assertEquals(Color(0xFF111113), HabitsDarkColorScheme.surface)
        assertEquals(Color(0xFF1C1C1F), HabitsDarkColorScheme.surfaceContainer)
        assertEquals(Color(0xFF2A2A2E), HabitsDarkColorScheme.surfaceVariant)
        assertEquals(Color(0xFF3A3A3E), HabitsDarkColorScheme.surfaceContainerHighest)
    }

    @Test
    fun `cards sit on a different surface slot from the app background`() {
        // The design's whole depth model is these two values. Collapsing them would make every
        // card disappear into the page, which is exactly the kind of change this guards against.
        assertNotEquals(HabitsDarkColorScheme.surface, HabitsDarkColorScheme.surfaceContainer)
    }

    @Test
    fun `brand and semantic colours carry the tokens from tokens md`() {
        assertEquals(Color(0xFF4A9EFF), HabitsDarkColorScheme.primary)
        assertEquals(Color(0xFFFF4D4D), HabitsDarkColorScheme.error)
        assertEquals(Color(0xFF4ADE80), HabitsDarkColors.success)
        assertEquals(Color(0xFFF59E0B), HabitsDarkColors.warning)
    }

    @Test
    fun `elevation tinting is off so a raised surface stays flat`() {
        // Material 3 blends surfaceTint into a surface as its elevation rises. The prototype has no
        // tonal elevation at all, so a tinted card would drift blue as soon as one gets shadow.
        assertEquals(Color.Transparent, HabitsDarkColorScheme.surfaceTint)
    }

    @Test
    fun `card borders are lighter than input borders`() {
        assertEquals(WHITE_06, HabitsDarkColorScheme.outlineVariant.alpha, ALPHA_TOLERANCE)
        assertEquals(WHITE_08, HabitsDarkColorScheme.outline.alpha, ALPHA_TOLERANCE)
        assertEquals(WHITE_20, HabitsDarkColors.focus.border.alpha, ALPHA_TOLERANCE)
    }

    @Test
    fun `the habit palette carries the four assignable colours in prototype order`() {
        assertEquals(
            listOf(
                Color(0xFF4A9EFF),
                Color(0xFF76C442),
                Color(0xFFFF7A35),
                Color(0xFF8B5CF6),
            ),
            HabitsDarkColors.habit.assignable,
        )
    }

    @Test
    fun `amber and grey exist but are not assignable to a habit`() {
        // Amber is chart-5 and grey is the Settings tab icon. Both are in the design; neither is a
        // habit colour, and a future session reaching for a fifth habit colour must not grab them.
        assertEquals(Color(0xFFF59E0B), HabitsDarkColors.habit.amber)
        assertEquals(Color(0xFF888888), HabitsDarkColors.habit.grey)
        assertEquals(FOUR_ASSIGNABLE_COLOURS, HabitsDarkColors.habit.assignable.size)
    }

    @Test
    fun `the default habit colour is the brand blue`() {
        assertEquals(HabitsDarkColorScheme.primary, HabitsDarkColors.habit.default)
    }

    @Test
    fun `the habit tint alphas match the hex bytes the prototype appends`() {
        val tint = HabitsDarkColors.tint

        assertEquals(0.157f, tint.iconCircle, ALPHA_TOLERANCE)
        assertEquals(0.149f, tint.monthDotIncomplete, ALPHA_TOLERANCE)
        assertEquals(0.133f, tint.yearDotIncomplete, ALPHA_TOLERANCE)
        assertEquals(0.125f, tint.weekPillIncomplete, ALPHA_TOLERANCE)
    }

    @Test
    fun `the text ramp is white at the nine documented opacities`() {
        val text = HabitsDarkColors.text

        assertEquals(Color.White, text.primary)
        assertEquals(WHITE_55, text.propertyChip.alpha, ALPHA_TOLERANCE)
        assertEquals(WHITE_50, text.statusLabel.alpha, ALPHA_TOLERANCE)
        assertEquals(WHITE_40, text.navLabel.alpha, ALPHA_TOLERANCE)
        assertEquals(WHITE_35, text.secondary.alpha, ALPHA_TOLERANCE)
        assertEquals(WHITE_30, text.tertiary.alpha, ALPHA_TOLERANCE)
        assertEquals(WHITE_25, text.eyebrow.alpha, ALPHA_TOLERANCE)
        assertEquals(WHITE_22, text.helper.alpha, ALPHA_TOLERANCE)
        assertEquals(WHITE_15, text.placeholder.alpha, ALPHA_TOLERANCE)
    }

    @Test
    fun `the text ramp descends without a repeated level`() {
        val text = HabitsDarkColors.text
        val ramp =
            listOf(
                text.primary,
                text.propertyChip,
                text.statusLabel,
                text.navLabel,
                text.secondary,
                text.tertiary,
                text.eyebrow,
                text.helper,
                text.placeholder,
            ).map { it.alpha }

        assertEquals(ramp.sortedDescending(), ramp)
        assertEquals(ramp.size, ramp.distinct().size)
    }

    @Test
    fun `the light scheme mirrors the dark one while v1 is dark only`() {
        // Not a designed light theme — ADR-009 makes v1 dark-only. This pins the fact that the
        // light entry point exists and is wired, so M2.4 has two schemes to choose between.
        assertEquals(HabitsDarkColorScheme, HabitsLightColorScheme)
        assertEquals(HabitsDarkColors, HabitsLightColors)
    }

    private companion object {
        /**
         * sRGB colours pack eight bits per channel, so an alpha of `0.55` is stored as `140/255` =
         * `0.549`. The tolerance covers that quantisation and nothing looser.
         */
        const val ALPHA_TOLERANCE = 0.005f

        const val FOUR_ASSIGNABLE_COLOURS = 4

        const val WHITE_55 = 0.55f
        const val WHITE_50 = 0.50f
        const val WHITE_40 = 0.40f
        const val WHITE_35 = 0.35f
        const val WHITE_30 = 0.30f
        const val WHITE_25 = 0.25f
        const val WHITE_22 = 0.22f
        const val WHITE_20 = 0.20f
        const val WHITE_15 = 0.15f
        const val WHITE_08 = 0.08f
        const val WHITE_06 = 0.06f
    }
}
