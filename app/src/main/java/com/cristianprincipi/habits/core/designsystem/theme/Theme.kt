package com.cristianprincipi.habits.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * The app's theme: the four token files in this package, wired into one wrapper.
 *
 * Everything Material 3 has a slot for goes into [MaterialTheme] — colours, type and shapes are
 * read back as `MaterialTheme.colorScheme`, `.typography` and `.shapes`. The two supplements it has
 * no slot for are provided as composition locals and read back through the [HabitsTheme] object:
 * `HabitsTheme.colors` and `HabitsTheme.textStyles`. Spacing is neither — [Spacing] and [Sizing]
 * are plain objects, per `CLAUDE.md §6`.
 *
 * **Dynamic colour is deliberately absent.** Material You would recolour a palette the designer
 * chose deliberately, from the user's wallpaper. There is no `dynamicDarkColorScheme` call here and
 * there should never be one.
 *
 * **Dark is forced, and [darkTheme] does not read the system setting.** v1 is dark-only (ADR-009);
 * the prototype never drew a light palette, so following the system into light mode would render an
 * undesigned theme. Note that the Android Studio template defaults this parameter to
 * `isSystemInDarkTheme()` — that is the thing not to "fix" it back to. The parameter exists so the
 * light/dark structure is wired and a real light palette post-v1 is a token change (ADR-020); until
 * then both branches resolve to the same values.
 *
 * `MainActivity` separately forces both system bars dark via `enableEdgeToEdge`, which is an
 * activity-level concern rather than a theme one — see ADR-019 for the bug that caused.
 */
@Composable
fun HabitsTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalHabitsColors provides if (darkTheme) HabitsDarkColors else HabitsLightColors,
        LocalHabitsTextStyles provides DefaultHabitsTextStyles,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) HabitsDarkColorScheme else HabitsLightColorScheme,
            typography = HabitsTypography,
            shapes = HabitsShapes,
            content = content,
        )
    }
}

/**
 * Accessors for the two token sets Material 3 has no home for, mirroring how `MaterialTheme` exposes
 * its own — a composable function and an object of the same name, resolved by use.
 */
object HabitsTheme {
    /** The habit palette, tint ramp, text-opacity ramp, focus, success and warning. */
    val colors: HabitsColors
        @Composable @ReadOnlyComposable
        get() = LocalHabitsColors.current

    /** Eyebrows, weekday initials, the numeric values and the monospaced fields. */
    val textStyles: HabitsTextStyles
        @Composable @ReadOnlyComposable
        get() = LocalHabitsTextStyles.current
}
