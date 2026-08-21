package com.cristianprincipi.habits.core.designsystem.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the bundled fonts are the ones actually drawing on a device.
 *
 * **A broken font is silent on Android**, which is why this test is shaped the way it is. Corrupting
 * `dm_sans.ttf`'s sfnt header and rebuilding was tried deliberately: the resource still packaged,
 * the APK still installed, nothing threw, and a test that merely rendered every style still passed —
 * the platform had quietly swapped in the system typeface. Nothing in the build catches that. Only
 * comparing against the fallback does, so [bundledFamiliesAreNotThePlatformFallback] is the
 * assertion that bites; re-running it against that same corrupted file fails, which is how it was
 * confirmed.
 *
 * [everyStyleRendersWithoutThrowing] stays as a layout smoke test — DM Sans is variable and every
 * style requests a different `opsz`, so each resolves its own typeface instance.
 */
@RunWith(AndroidJUnit4::class)
class TypographyRenderTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun everyStyleRendersWithoutThrowing() {
        val styles = materialSlots() + extendedStyles()

        composeTestRule.setContent {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                styles.forEach { style -> Text(text = SPECIMEN, style = style) }
            }
        }

        composeTestRule.onAllNodesWithText(SPECIMEN).assertCountEquals(styles.size)
    }

    @Test
    fun bundledFamiliesAreNotThePlatformFallback() {
        lateinit var measurer: TextMeasurer
        composeTestRule.setContent { measurer = rememberTextMeasurer() }

        composeTestRule.runOnIdle {
            // If a bundled file fails to load, the platform substitutes its own sans and the two
            // measurements collapse onto the same number. Identical widths here mean the app is
            // rendering in the system font while claiming to render in DM Sans.
            assertNotEquals(
                "DM Sans measured identically to the platform fallback",
                measurer.widthOf(fallbackOf(HabitsTypography.bodyLarge)),
                measurer.widthOf(HabitsTypography.bodyLarge),
            )
            assertNotEquals(
                "DM Mono measured identically to the platform fallback",
                measurer.widthOf(fallbackOf(HabitsDarkTextStyles.mono)),
                measurer.widthOf(HabitsDarkTextStyles.mono),
            )
            // DM Sans and DM Mono are different designs at the same size; if either had fallen back
            // to the same platform face, these would match each other too.
            assertNotEquals(
                "DM Sans and DM Mono measured identically",
                measurer.widthOf(HabitsDarkTextStyles.mono),
                measurer.widthOf(HabitsTypography.bodyMedium),
            )
        }
    }

    private fun TextMeasurer.widthOf(style: TextStyle) = measure(text = SPECIMEN, style = style).size.width

    /** The same style with the bundled family removed — what a failed font load degrades to. */
    private fun fallbackOf(style: TextStyle) = style.copy(fontFamily = FontFamily.Default)

    private fun materialSlots(): List<TextStyle> =
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

    private fun extendedStyles(): List<TextStyle> =
        listOf(
            HabitsDarkTextStyles.eyebrow,
            HabitsDarkTextStyles.eyebrowSmall,
            HabitsDarkTextStyles.weekdayInitial,
            HabitsDarkTextStyles.statValue,
            HabitsDarkTextStyles.metricValue,
            HabitsDarkTextStyles.mono,
            HabitsDarkTextStyles.monoSmall,
        )

    private companion object {
        /** Long enough that two different typefaces cannot measure the same by accident. */
        const val SPECIMEN = "Habits 0123456789 the quick brown fox"
    }
}
