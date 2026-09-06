package com.cristianprincipi.habits.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M13.4 — **every foreground the app draws, against every background it draws it on.**
 *
 * ### Why this is a JVM test and not a screenshot
 *
 * A contrast ratio is a pure function of two colours. M13.1, M13.2 and M13.3 all had to ask the
 * running app because layout, semantics and font scaling are decided at runtime; this is arithmetic
 * over `Color.kt` and nothing else, so it belongs in the fast suite where it runs on every commit.
 *
 * ### ⚠⚠ What the audit found, and why the palette moved
 *
 * The design's ramp ran `1.00 · 0.55 · 0.50 · 0.40 · 0.35 · 0.30 · 0.25 · 0.22 · 0.15`, and **six of
 * those nine were below AA**: 3.81, 3.19, 2.66, 2.20, 1.97, 1.54 against the 4.5 that normal text
 * requires. All six are drawn at 9–14sp, so none reached the 3:1 large-text allowance either.
 *
 * ⚠ **A nine-step white-opacity ramp on near-black cannot meet AA by nudging** — the minimum alpha
 * is 0.46 for every one of them, so they all collapse onto a single value. The user chose to
 * re-space the ramp by *ratio* instead, keeping nine distinct levels (ADR-111).
 *
 * ### What this file is for now
 *
 * Not to re-derive that decision, but to stop it drifting. Every level is pinned to the ratio it was
 * chosen for, so **editing an alpha, or the background behind it, fails here** rather than shipping.
 */
class ContrastTest {
    /**
     * WCAG 2.1 relative luminance, and the `sRGB` gamma expansion it is defined in terms of.
     *
     * ⚠ Written out rather than taken from a library: it is nine lines, and a dependency whose
     * rounding differs from the specification would make every number below unfalsifiable.
     */
    private fun luminance(color: Color): Double {
        fun channel(c: Float): Double {
            val v = c.toDouble()
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    /**
     * The ratio between two **opaque** colours.
     *
     * ⚠ Both arguments must already be composited. A translucent foreground has no contrast of its
     * own — what a person sees is the blend, and blending is what [over] does.
     */
    private fun contrast(
        a: Color,
        b: Color,
    ): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    /** [foreground] drawn onto [background], in the gamma space Compose actually blends in. */
    private fun over(
        foreground: Color,
        background: Color,
    ): Color =
        Color(
            red = foreground.alpha * foreground.red + (1 - foreground.alpha) * background.red,
            green = foreground.alpha * foreground.green + (1 - foreground.alpha) * background.green,
            blue = foreground.alpha * foreground.blue + (1 - foreground.alpha) * background.blue,
        )

    /** The ratio a person actually sees: [foreground] composited onto [background], against it. */
    private fun ratio(
        foreground: Color,
        background: Color,
    ): Double = contrast(over(foreground, background), background)

    private val scheme = HabitsDarkColorScheme
    private val colors = HabitsDarkColors

    /** The app background and a card — the only two surfaces any of this text is ever drawn on. */
    private val surfaces = listOf("background" to scheme.surface, "card" to scheme.surfaceContainer)

    private fun worstCase(foreground: Color): Double = surfaces.minOf { (_, bg) -> ratio(foreground, bg) }

    private fun HabitsColors.disabled(): Color = text.disabled

    /**
     * ⚠⚠ **The step's own wording says "light and dark", and there is no light theme.**
     *
     * Checked before anything else, the way M12.9 and M13.1 found their own wording out of date. Both
     * light aliases are the dark values, and their KDocs say so: v1 is dark-only, and the light
     * scheme exists so the *structure* is wired, not because a light palette was ever designed.
     *
     * If this ever fails, a real light palette has appeared and **every ratio below has to be
     * measured again against it** — none of them carries over.
     */
    @Test
    fun thereIsOnlyOnePaletteToCheck() {
        assertEquals(HabitsDarkColorScheme, HabitsLightColorScheme)
        assertEquals(HabitsDarkColors, HabitsLightColors)
    }

    /**
     * Every level of the foreground ramp, at the ratio it was chosen for.
     *
     * ⭐ Asserted as **a band rather than a floor**, deliberately. A floor would let the whole ramp
     * drift upward — brightening `helper` to `primary` would pass a "≥ 4.5" test while destroying
     * the hierarchy the nine levels exist to express. The band says "this level, this brightness".
     */
    @Test
    fun everyForegroundLevelSitsAtTheRatioItWasChosenFor() {
        val expected =
            listOf(
                Triple("primary", colors.text.primary, 17.0),
                Triple("propertyChip", colors.text.propertyChip, 14.5),
                Triple("statusLabel", colors.text.statusLabel, 12.3),
                Triple("navLabel", colors.text.navLabel, 10.5),
                Triple("secondary", colors.text.secondary, 8.9),
                Triple("tertiary", colors.text.tertiary, 7.6),
                Triple("eyebrow", colors.text.eyebrow, 6.4),
                Triple("helper", colors.text.helper, 5.5),
                Triple("placeholder", colors.text.placeholder, 4.6),
            )

        val drifted =
            expected.mapNotNull { (name, color, target) ->
                val actual = worstCase(color)
                "$name is %.2f:1, chosen at %.1f:1"
                    .format(actual, target)
                    .takeIf { Math.abs(actual - target) > TOLERANCE }
            }
        assertTrue(drifted.joinToString(prefix = "\n", separator = "\n"), drifted.isEmpty())
    }

    /**
     * ⚠⚠ **AA for normal text, and every one of these is normal text.**
     *
     * The 3:1 large-text allowance needs 18.66sp, or 14sp bold. The largest thing on this ramp below
     * `primary` is the nav date label at **14sp SemiBold**, which does not reach it — so 4.5 is the
     * bar for the whole ramp and there is no level that gets a discount.
     */
    @Test
    fun everyForegroundLevelMeetsAaForNormalText() {
        val levels =
            mapOf(
                "primary" to colors.text.primary,
                "propertyChip" to colors.text.propertyChip,
                "statusLabel" to colors.text.statusLabel,
                "navLabel" to colors.text.navLabel,
                "secondary" to colors.text.secondary,
                "tertiary" to colors.text.tertiary,
                "eyebrow" to colors.text.eyebrow,
                "helper" to colors.text.helper,
                "placeholder" to colors.text.placeholder,
            )

        val failures =
            levels
                .mapNotNull { (name, color) ->
                    surfaces
                        .mapNotNull { (surface, bg) ->
                            val r = ratio(color, bg)
                            "$name on $surface is %.2f:1, below AA's %.1f".format(r, AA_TEXT).takeIf { r < AA_TEXT }
                        }.ifEmpty { null }
                }.flatten()

        assertTrue(failures.joinToString(prefix = "\n", separator = "\n"), failures.isEmpty())
    }

    /**
     * The nine levels stay **nine**, and in order.
     *
     * ⭐ The point of re-spacing rather than collapsing: had the failing six been raised to the 0.46
     * minimum they would all have landed on one value, and this is what says they did not.
     */
    @Test
    fun theRampIsStillNineDistinctLevelsInDescendingOrder() {
        val ramp =
            listOf(
                colors.text.primary,
                colors.text.propertyChip,
                colors.text.statusLabel,
                colors.text.navLabel,
                colors.text.secondary,
                colors.text.tertiary,
                colors.text.eyebrow,
                colors.text.helper,
                colors.text.placeholder,
            ).map { worstCase(it) }

        assertEquals(
            "two levels have collapsed onto the same ratio: $ramp",
            9,
            ramp.map { "%.1f".format(it) }.toSet().size,
        )
        assertEquals("the ramp is no longer monotonic: $ramp", ramp.sortedDescending(), ramp)
    }

    /**
     * ⚠⚠ **An unticked week pill has to be visible as a control** — WCAG 1.4.11, 3:1.
     *
     * At the design's fill alone it was **1.21:1** against its card. ⚠ **Raising the fill cannot fix
     * it**: 3:1 needs alpha 0.79 for purple, which draws an unticked day at nearly the ticked colour
     * and destroys the state distinction the criterion exists to protect. So the pill is outlined,
     * and it is the outline that is measured here.
     */
    @Test
    fun anUntickedWeekPillIsVisibleAsAControl() {
        val outline = colors.text.primary.copy(alpha = colors.tint.weekPillOutline)
        val r = ratio(outline, scheme.surfaceContainer)
        assertTrue("an unticked pill's outline is %.2f:1, below 1.4.11's %.1f".format(r, AA_NON_TEXT), r >= AA_NON_TEXT)
    }

    /**
     * ⚠⚠ **The three form controls have to be visible as controls too** — WCAG 1.4.11, 3:1.
     *
     * ⭐ **M13.10 closed the one failure ADR-111 §7 left open on purpose.** The text field's resting
     * border was **1.26:1** against its card, which M13.4 measured, named as real, and deliberately
     * did not fix — a different component from the ramp it was auditing, and one deserving its own
     * decision. The user made that decision in the QA sweep: raise it, the same way the week pill was
     * raised, with white rather than a brighter tint.
     *
     * They share `outline`, so this asserts the token once and names all three: the setup token and
     * link fields, the day-key radio, and the habit checkbox. ⚠ **The two status dots are NOT in
     * here** — they moved to `dotInactive` precisely so this floor could not drag them along, and
     * 1.4.11 does not reach a decorative dot beside text that says the same thing in words.
     */
    @Test
    fun aRestingFormControlIsVisibleAsAControl() {
        val r = ratio(scheme.outline, scheme.surfaceContainer)
        assertTrue(
            "a control's resting border is %.2f:1, below 1.4.11's %.1f".format(r, AA_NON_TEXT),
            r >= AA_NON_TEXT,
        )
    }

    /**
     * ⚠⚠ **A focused field must be the stronger of the two, and M13.10 nearly shipped it backwards.**
     *
     * Raising the resting border to clear 1.4.11 took it to 0.35 — past the focus border's 0.20,
     * which had been fine when resting was 0.08. **Focusing a field would have made its outline
     * fainter**: a defect introduced by the fix, invisible to every assertion that existed, and
     * exactly the kind a number-by-number audit misses because both numbers are individually
     * defensible.
     *
     * Asserted as a **relationship** rather than a pair of values, so the two cannot cross again
     * whatever either is set to.
     */
    @Test
    fun aFocusedFieldOutranksItsRestingBorder() {
        val resting = ratio(scheme.outline, scheme.surfaceContainer)
        val focused = ratio(colors.focus.border, scheme.surfaceContainer)
        assertTrue(
            "focus is %.2f:1 against a resting %.2f:1 — focusing makes the field fainter".format(focused, resting),
            focused > resting,
        )
    }

    /**
     * A **ticked** pill is carried by its fill, in all four habit colours.
     *
     * The state distinction is the fill; the outline above is only about the empty case. Purple is
     * the one to watch — it is the darkest of the four and clears 1.4.11 by the least.
     */
    @Test
    fun aTickedWeekPillIsCarriedByItsFillInEveryHabitColour() {
        val failures =
            colors.habit.assignable.mapNotNull { habit ->
                val r = ratio(habit, scheme.surfaceContainer)
                "a ticked pill is %.2f:1".format(r).takeIf { r < AA_NON_TEXT }
            }
        assertTrue(failures.joinToString(), failures.isEmpty())
    }

    /**
     * ⚠⚠ **One level is deliberately below AA, and this is where that is on the record.**
     *
     * WCAG 1.4.3 exempts "inactive user interface components" by name. `disabled` is the design's
     * old `tertiary` kept at 30% for exactly that case — because raising the ramp took a disabled
     * button from *white against 30% white* to *white against 64%*, and a control that no longer
     * looks disabled is the worse accessibility outcome: nothing tells you it will not respond.
     *
     * ⭐ Asserted as a **ceiling**, which is the opposite of every other test here. The risk with an
     * exemption is not that it degrades; it is that it spreads. If this level ever climbs to where a
     * live level sits, it has stopped being a disabled state and the exemption stops applying.
     */
    @Test
    fun theOneLevelBelowAaIsTheDisabledOneAndItStaysThere() {
        val r = worstCase(colors.disabled())
        assertTrue(
            "the disabled level is %.2f:1 — bright enough that it no longer reads as disabled".format(r),
            r < AA_TEXT,
        )
        assertTrue("the disabled level is %.2f:1 — too faint to see at all".format(r), r > 2.0)
    }

    /**
     * ⚠ **The control, and without it every assertion above is unfalsifiable.**
     *
     * ADR-108 §2 and ADR-110 §6 both caught a check that could not fail. This one is arithmetic, so
     * the way it fails is by being *wrong* arithmetic — and the way to rule that out is the two
     * ratios WCAG itself fixes: black on white is exactly 21:1, and any colour on itself is 1:1.
     */
    @Test
    fun andTheArithmeticItselfIsRight() {
        assertEquals(21.0, contrast(Color.Black, Color.White), 0.01)
        assertEquals(1.0, contrast(Color.White, Color.White), 0.001)
        assertEquals(1.0, contrast(scheme.surface, scheme.surface), 0.001)

        // A translucent foreground must be composited, not measured raw: white at 15% over the card
        // is the ~1.59:1 the design shipped, and reading its alpha as opaque would say 17:1.
        assertEquals(1.59, ratio(Color.White.copy(alpha = 0.15f), scheme.surfaceContainer), 0.02)
    }

    private companion object {
        /** WCAG 2.1 SC 1.4.3, normal text. */
        const val AA_TEXT = 4.5

        /** WCAG 2.1 SC 1.4.11, user interface components and graphical objects. */
        const val AA_NON_TEXT = 3.0

        /**
         * The ramp's alphas are whole `n/255` steps chosen just **above** each target, so a level
         * lands within a step of it — never below. Wide enough to absorb that, narrow enough that
         * moving a level to its neighbour's brightness fails.
         */
        const val TOLERANCE = 0.2
    }
}
