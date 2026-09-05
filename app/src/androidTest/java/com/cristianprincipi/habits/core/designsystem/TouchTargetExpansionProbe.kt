package com.cristianprincipi.habits.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.core.designsystem.component.SecondaryButton
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ⭐⭐ M13.1 — **is a 40dp button tappable across 48dp, or only drawn at 40?**
 *
 * ### Why this exists rather than a paragraph of reasoning
 *
 * `AccessibilitySweepTest` found every button in the app measuring **40dp tall** — that is
 * `ButtonDefaults.MinHeight`, a Material token, against a 48dp guideline `CLAUDE.md` §6 requires.
 * Whether that is a defect or a non-event depends entirely on a fact about Compose that three
 * different rectangles on `SemanticsNode` disagree about: `size` says 40, `touchBoundsInRoot` says
 * 48, and `boundsInRoot` says whatever the clipping allows.
 *
 * **Reading more source would not settle it, and guessing would decide twelve controls.** So this
 * asks the input system directly: it taps a point that is **inside the 48dp band and outside the
 * drawn button**, and reports whether the click arrives.
 *
 * The answer is what M13.1 acts on, and it is recorded in ADR-108 either way.
 */
@RunWith(AndroidJUnit4::class)
class TouchTargetExpansionProbe {
    @get:Rule
    val composeTestRule = createComposeRule()

    private var clicks = 0

    /** A real app button, centred with room around it so a near-miss lands on nothing else. */
    private fun showButton() {
        composeTestRule.setContent {
            HabitsTheme {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(300.dp, 200.dp), contentAlignment = Alignment.Center) {
                        SecondaryButton(
                            text = "Try again",
                            onClick = { clicks++ },
                            modifier = Modifier.testTag(BUTTON),
                        )
                    }
                }
            }
        }
    }

    /**
     * A tap 1dp above the button's own top edge.
     *
     * Inside the 48dp band that `touchBoundsInRoot` claims, outside the 40dp the button draws. If
     * Compose expands the hit area for a minimum touch target, this registers; if it does not, this
     * lands on the parent and the counter stays at zero.
     */
    private fun tapAbove(dpAboveTheEdge: Int) {
        composeTestRule.onNodeWithTag(BUTTON).performTouchInput {
            click(Offset(x = width / 2f, y = with(density) { (-dpAboveTheEdge).dp.toPx() }))
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun aTapJustAboveTheDrawnEdgeStillArrives() {
        showButton()

        tapAbove(JUST_OUTSIDE_DP)

        // ⚠ Recorded rather than asserted-as-hoped: this test's job is to *report* the platform's
        // behaviour, and its expected value was written only after the first run said what it is.
        assertEquals(
            "a tap ${JUST_OUTSIDE_DP}dp above the drawn edge of a ${BUTTON_HEIGHT_DP}dp button",
            EXPANSION_REACHES_OUTSIDE_THE_DRAWN_EDGE,
            clicks == 1,
        )
    }

    /**
     * ⚠⚠ **The control, and without it the test above proves nothing.**
     *
     * `performTouchInput` takes coordinates in the node's own space, and a harness that *clamped* a
     * negative offset back inside the node would make the first test pass no matter how Compose
     * behaves. So this taps far outside — well beyond any minimum-target expansion — and requires
     * the click **not** to arrive. Both passing would mean the coordinates are being clamped and
     * neither result means anything.
     */
    @Test
    fun butATapFarOutsideDoesNot() {
        showButton()

        tapAbove(FAR_OUTSIDE_DP)

        assertEquals("a tap ${FAR_OUTSIDE_DP}dp above the button reached it — coordinates are clamped", 0, clicks)
    }

    private companion object {
        const val BUTTON = "probe-button"
        const val BUTTON_HEIGHT_DP = 40

        /** Outside the drawn 40dp, inside the 48dp band — 4dp of expansion sits on each edge. */
        const val JUST_OUTSIDE_DP = 1

        /** Far beyond any expansion, and the control for the harness clamping coordinates. */
        const val FAR_OUTSIDE_DP = 40

        /**
         * Filled in from the first run — see the KDoc. Compose expands a clickable's hit area to the
         * minimum touch target, so a tap outside the drawn button still arrives.
         */
        const val EXPANSION_REACHES_OUTSIDE_THE_DRAWN_EDGE = true
    }
}
