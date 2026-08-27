package com.cristianprincipi.habits.feature.notionsetup

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The setup wizard in a viewport too short for it — which is what a raised keyboard makes.
 *
 * ### The defect this pins, found by M5.5 on the emulator
 *
 * The wizard pads itself with `WindowInsets.safeDrawing`, and `safeDrawing` **includes the IME**.
 * So opening the keyboard does not draw over the wizard, it shrinks it. [TokenStep] was a plain
 * `Column`, and a `Column` handed less room than its content needs clips the overflow in silence.
 *
 * In landscape with the keyboard up, that clipped away the token field, its helper line and
 * **Continue** — leaving a screen where a user could type a token and had no way to submit it.
 * Setup could not be completed in landscape at all. It is the same failure as ADR-055, one step
 * earlier in the same wizard.
 *
 * ### Why a fixed-height Box rather than a real keyboard
 *
 * A test cannot reliably raise the IME, and the IME is not the point — the shortage of room is.
 * A [SHORT_VIEWPORT] Box reproduces exactly what the keyboard does to the layout, on any device,
 * with no rotation and no soft-input timing to wait for.
 *
 * ⚠ **`performScrollTo` is what makes this a real assertion.** It fails outright when no scrollable
 * ancestor exists, so this test cannot pass by accident on a `Column` that has quietly lost its
 * `verticalScroll` — which is the regression worth catching.
 */
@RunWith(AndroidJUnit4::class)
class NotionSetupShortViewportTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun string(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

    private fun showInAShortViewport(
        state: NotionSetupUiState,
        height: Dp = SHORT_VIEWPORT,
    ) {
        composeTestRule.setContent {
            HabitsTheme {
                Box(modifier = Modifier.fillMaxWidth().height(height)) {
                    NotionSetupContent(state = state, onEvent = {})
                }
            }
        }
    }

    @Test
    fun continueStaysReachableOnTheTokenStepWhenThereIsNoRoom() {
        showInAShortViewport(NotionSetupUiState(token = "ntn_not-a-real-token"))

        composeTestRule
            .onNodeWithText(string(R.string.setup_token_continue))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun theTokenFieldStaysReachableWhenThereIsNoRoom() {
        // The field and the button are at opposite ends of the step, so reaching one says nothing
        // about the other: a scroll container that starts at the top hides the button, and one
        // scrolled to the button hides the field.
        showInAShortViewport(NotionSetupUiState())

        composeTestRule
            .onNodeWithText(string(R.string.setup_token_label))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun theDoneStepStaysReadableWhenThereIsNoRoom() {
        // No keyboard ever opens here, but it is the wizard's other plain Column and so has the
        // same failure available to it.
        showInAShortViewport(
            NotionSetupUiState(step = NotionSetupStep.Done, importedDays = 513),
        )

        composeTestRule
            .onNodeWithText(string(R.string.setup_done_title))
            .performScrollTo()
            .assertIsDisplayed()
    }

    private companion object {
        /**
         * Shorter than the token step needs, and close to what is left of a landscape phone once
         * the keyboard is up — 411dp tall, less the keyboard's share.
         */
        val SHORT_VIEWPORT = 220.dp
    }
}
