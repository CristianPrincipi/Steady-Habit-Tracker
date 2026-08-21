package com.cristianprincipi.habits.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cristianprincipi.habits.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves `HabitsTheme` actually hands every token set to the composables underneath it.
 *
 * A theme wrapper is easy to get half-wired — provide the colours and forget the shapes, and nothing
 * fails until some screen quietly renders with Material's defaults. These assertions read the tokens
 * back out of a real composition rather than from the constants they were built from.
 */
@RunWith(AndroidJUnit4::class)
class ThemeTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun themeProvidesEveryTokenSet() {
        lateinit var colorScheme: ColorScheme
        lateinit var typography: Typography
        lateinit var shapes: Shapes
        lateinit var colors: HabitsColors
        lateinit var textStyles: HabitsTextStyles

        composeTestRule.setContent {
            HabitsTheme {
                colorScheme = MaterialTheme.colorScheme
                typography = MaterialTheme.typography
                shapes = MaterialTheme.shapes
                colors = HabitsTheme.colors
                textStyles = HabitsTheme.textStyles
            }
        }

        composeTestRule.runOnIdle {
            assertEquals(HabitsDarkColorScheme, colorScheme)
            assertEquals(HabitsTypography, typography)
            assertEquals(HabitsShapes, shapes)
            assertEquals(HabitsDarkColors, colors)
            assertEquals(DefaultHabitsTextStyles, textStyles)
        }
    }

    @Test
    fun theThemeIsDarkByDefaultRegardlessOfTheSystemSetting() {
        // v1 is dark-only (ADR-009). The Android Studio template defaults this parameter to
        // isSystemInDarkTheme(); ours must not, or a device in light mode renders an undesigned
        // palette. Asserting the default argument is what pins that.
        // `Color` is a value class, so it cannot be `lateinit`. Unspecified is the sentinel: if the
        // composition never ran, the assertion fails on it rather than passing vacuously.
        var background = Color.Unspecified

        composeTestRule.setContent {
            HabitsTheme { background = MaterialTheme.colorScheme.background }
        }

        composeTestRule.runOnIdle {
            assertEquals(HabitsDarkColorScheme.background, background)
        }
    }

    @Test
    fun defaultTextStyleIsTheBundledSansNotThePlatformDefault() {
        // MaterialTheme seeds LocalTextStyle from typography.bodyLarge, so any Text() without an
        // explicit style inherits our family. If the typography were not provided, this would fall
        // back to TextStyle.Default, whose fontFamily is null and whose font is the platform's.
        //
        // Compared field by field rather than whole: Material3 merges its own paragraph defaults
        // (platformStyle, lineHeightStyle) into what it provides, so the object is bodyLarge plus
        // those and never equal to it.
        lateinit var inherited: TextStyle

        composeTestRule.setContent {
            HabitsTheme { inherited = LocalTextStyle.current }
        }

        composeTestRule.runOnIdle {
            assertEquals(HabitsTypography.bodyLarge.fontFamily, inherited.fontFamily)
            assertEquals(HabitsTypography.bodyLarge.fontSize, inherited.fontSize)
            assertEquals(HabitsTypography.bodyLarge.fontWeight, inherited.fontWeight)
            assertNotEquals(TextStyle.Default.fontFamily, inherited.fontFamily)
        }
    }

    @Test
    fun theXmlWindowBackgroundMatchesTheComposeBackground() {
        // The window background is painted before any Compose frame exists, so it has to be an XML
        // resource — the one colour that cannot live in Color.kt. If the two drift apart, cold start
        // flashes the wrong colour, which is invisible in every other test and hard to catch by eye.
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        assertEquals(
            HabitsDarkColorScheme.background.toArgb(),
            context.getColor(R.color.window_background),
        )
    }
}
