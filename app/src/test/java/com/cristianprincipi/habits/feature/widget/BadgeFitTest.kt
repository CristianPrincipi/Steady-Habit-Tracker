package com.cristianprincipi.habits.feature.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M15.6g — fitting a number into the badge an emoji was designed for.
 *
 * ### ⚠ What this pins, and what it deliberately does not
 *
 * It pins the **rule**: start at the design size, step down only while the text does not fit, never
 * go below the floor, and leave the size alone when it already fits. It does **not** pin Roboto's
 * metrics — [widthAt] below is a linear model of them, and the production path measures the real
 * typeface with a real `Paint`. A test that hardcoded "84.3 is 25.2dp at 13sp" would be asserting
 * the font vendor's numbers, and would fail on a device that shipped a different one.
 *
 * ⭐ The advances are Roboto's own (digit 1139/2048 em, period 553/2048), so the model is close
 * enough that the sizes below are the sizes a phone actually chooses — but every assertion is
 * written against the *fit*, not against the number.
 *
 * ### The values are the user's, not invented
 *
 * `84.3` and `7.5` are Weight and Sleep from `docs/notion-schema.md` §2.4 — one four characters
 * wide, one three. `1234.5` is the case that does not exist yet: a measure counted in steps rather
 * than kilograms, which is exactly the input that would have clipped silently.
 */
class BadgeFitTest {
    @Test
    fun theUsersOwnAveragesFitAtTheDesignSizeAndAreLeftAlone() {
        // ⭐ The whole point of the floor-and-step being conditional: at 100% font scale nothing
        // moves, so this change cannot have altered what is on their home screen today.
        assertEquals(MAX_BADGE_SP, fitAt("84.3"), 0f)
        assertEquals(MAX_BADGE_SP, fitAt("7.5"), 0f)
        assertEquals(MAX_BADGE_SP, fitAt("—"), 0f)
    }

    @Test
    fun aLargeFontScaleShrinksTheNumberRatherThanClippingIt() {
        // ⚠ The mismatch this function exists for: the text grows with the font scale and the 36dp
        // circle does not. At 150% the design size no longer fits, and the fitted one must.
        val fitted = fitAt("84.3", fontScale = 1.5f)

        assertTrue("$fitted did not step down from $MAX_BADGE_SP", fitted < MAX_BADGE_SP)
        assertTrue("$fitted is below the floor", fitted >= MIN_BADGE_SP)
        assertFits("84.3", fitted, fontScale = 1.5f)
    }

    @Test
    fun aValueLongerThanTheUsersOwnStillFits() {
        val fitted = fitAt("1234.5")

        assertTrue("$fitted did not step down from $MAX_BADGE_SP", fitted < MAX_BADGE_SP)
        assertFits("1234.5", fitted)
    }

    @Test
    fun theFloorHoldsWhenNothingWillFitAndTheCallerClipsInstead() {
        // Six characters at Android's top font scale. Nothing readable fits, and the answer is the
        // floor rather than a 3sp smear — MIN_BADGE_SP still draws 16sp at this scale.
        assertEquals(MIN_BADGE_SP, fitAt("1234.5", fontScale = 2f), 0f)
    }

    @Test
    fun aCircleWithNoWidthAsksForTheFloorRatherThanLoopingToNothing() {
        assertEquals(MIN_BADGE_SP, fitBadgeSp(availablePx = 0f) { 1f }, 0f)
    }

    @Test
    fun theFitIsIndependentOfScreenDensity() {
        // Density scales the circle and the text together, so a 420dpi emulator and a 560dpi phone
        // must choose the same size. If they did not, every number here would be a screenshot's
        // worth of luck.
        assertEquals(fitAt("84.3", density = 2.625f), fitAt("84.3", density = 3.5f), 0f)
        assertEquals(fitAt("1234.5", density = 2.625f), fitAt("1234.5", density = 3.5f), 0f)
    }

    private fun fitAt(
        text: String,
        density: Float = PHONE_DENSITY,
        fontScale: Float = 1f,
    ): Float = fitBadgeSp(availablePx = availablePx(density)) { sp -> widthAt(text, sp, density, fontScale) }

    private fun assertFits(
        text: String,
        sp: Float,
        density: Float = PHONE_DENSITY,
        fontScale: Float = 1f,
    ) {
        val width = widthAt(text, sp, density, fontScale)
        val available = availablePx(density)
        assertTrue("\"$text\" is ${width}px at ${sp}sp in ${available}px", width <= available)
    }

    private fun availablePx(density: Float) = BADGE_SIZE.value * density * BADGE_TEXT_WIDTH_RATIO

    /** Roboto's advances, linear in text size — see the class comment on why this is a model. */
    private fun widthAt(
        text: String,
        sp: Float,
        density: Float,
        fontScale: Float,
    ): Float {
        val ems =
            text.sumOf { char ->
                when {
                    char.isDigit() -> DIGIT_EM
                    char == '.' -> PERIOD_EM
                    else -> WIDE_EM
                }.toDouble()
            }
        return ems.toFloat() * sp * density * fontScale
    }

    private companion object {
        /** A modern phone, near the user's own OnePlus 12. */
        const val PHONE_DENSITY = 3.0f
        const val DIGIT_EM = 1139f / 2048f
        const val PERIOD_EM = 553f / 2048f

        /** An em dash, and anything else that is not a digit — the widest thing a badge can hold. */
        const val WIDE_EM = 1f
    }
}
