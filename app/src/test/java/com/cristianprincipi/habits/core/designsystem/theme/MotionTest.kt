package com.cristianprincipi.habits.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.TweenSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the app's one motion value against `docs/tokens.md §6`.
 *
 * ### Why the curve is sampled instead of compared by name
 *
 * [Motion.stateChangeEasing] is [androidx.compose.animation.core.FastOutSlowInEasing], and asserting
 * *that* would prove only that we still point at the same androidx constant. What actually has to be
 * true is that the app eases on **Tailwind's default curve**, `cubic-bezier(0.4, 0, 0.2, 1)`, which
 * is what the prototype's every `transition-*` runs on. So the test builds that bezier from the four
 * numbers in the CSS spec and requires the app's easing to agree with it point by point. If androidx
 * ever redefines its constant, this fails — which is the outcome we want, not a false pass.
 *
 * The same reasoning as `ShapeAndSpacingTest`: assert the value, and assert a wrong-but-plausible
 * neighbour is *not* it.
 */
class MotionTest {
    /** `cubic-bezier(0.4, 0, 0.2, 1)` — Tailwind's `--default-transition-timing-function`. */
    private val tailwindDefault = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)

    /**
     * `cubic-bezier(0, 0, 0.2, 1)` — Material's *decelerate* curve, and the plausible wrong answer.
     *
     * It shares three of its four numbers with the right one and differs only in where it starts, so
     * it is exactly the substitution a future session could make without noticing.
     */
    private val decelerate = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)

    private val samples = listOf(0.1f, 0.25f, 0.4f, 0.5f, 0.6f, 0.75f, 0.9f)

    @Test
    fun `the state change lasts tailwind's default 150ms`() {
        val spec = Motion.colorStateChange as TweenSpec<*>

        assertEquals(Motion.STATE_CHANGE_MILLIS, spec.durationMillis)
        assertEquals(TAILWIND_DEFAULT_MILLIS, spec.durationMillis)
        // A delay would make the first frames of every check-off a dead pause.
        assertEquals(0, spec.delay)
    }

    @Test
    fun `the easing is the cubic-bezier tailwind applies, not merely the constant androidx names`() {
        samples.forEach { t ->
            assertEquals(
                "eased value at t=$t",
                tailwindDefault.transform(t).toDouble(),
                Motion.stateChangeEasing.transform(t).toDouble(),
                TOLERANCE,
            )
        }
    }

    @Test
    fun `and it is not the decelerate curve it is one number away from`() {
        // The two curves are closest at the ends and furthest apart early, which is why this asserts
        // over a sample rather than at a single point: `t = 0.9` alone would not separate them.
        val differs =
            samples.any { t ->
                kotlin.math.abs(decelerate.transform(t) - Motion.stateChangeEasing.transform(t)) > TOLERANCE
            }

        assertTrue("the app's easing is indistinguishable from cubic-bezier(0, 0, .2, 1)", differs)
    }

    @Test
    fun `a colour and a float cross on the same terms`() {
        val forColor = Motion.colorStateChange as TweenSpec<*>
        val forFloat = Motion.floatStateChange as TweenSpec<*>

        // Two types, one rule. A pill's fill and a button's opacity settling at different moments is
        // the sort of thing nobody reports and everybody feels.
        assertEquals(forColor.durationMillis, forFloat.durationMillis)
        assertEquals(forColor.easing, forFloat.easing)
    }

    @Test
    fun `the duration is not navigation compose's 700ms default`() {
        // ⚠⚠ The animation M12.5 removed. `NavHost` defaults to `fadeIn(tween(700))`, and until this
        // step every tab switch in the app ran on it — 4.7× the design's figure, chosen by nobody.
        // Written here so the number that was wrong is recorded next to the number that is right.
        assertNotEquals(NAV_HOST_DEFAULT_MILLIS, Motion.STATE_CHANGE_MILLIS)
    }

    private companion object {
        const val TAILWIND_DEFAULT_MILLIS = 150
        const val NAV_HOST_DEFAULT_MILLIS = 700
        const val TOLERANCE = 0.001
    }
}
