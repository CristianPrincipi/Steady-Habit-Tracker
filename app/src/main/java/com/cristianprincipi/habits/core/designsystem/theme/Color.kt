package com.cristianprincipi.habits.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Colour, ported from the Figma Make prototype.
 *
 * Source of truth: `docs/tokens.md §1`, itself extracted from the cached prototype in
 * `docs/figma-source/`. Where `theme.css` and `App.tsx` disagree, `App.tsx` wins — it is what the
 * designer actually looked at (`docs/tokens.md §0`). Do not re-fetch from Figma to answer a
 * question this file or `docs/tokens.md` already answers; the MCP budget is 20 reads a month.
 *
 * `CLAUDE.md §6` makes this file one of the very few allowed to hold a raw colour literal.
 * Everywhere else names a colour by role, through `MaterialTheme.colorScheme` or [HabitsColors].
 */

// ---------------------------------------------------------------------------------------------
// Raw palette — docs/tokens.md §1.1-§1.3. Private: nothing outside this file names a value.
// ---------------------------------------------------------------------------------------------

/** Every foreground in the design is white at an opacity — see [TextEmphasis]. */
private fun white(alpha: Float) = Color.White.copy(alpha = alpha)

private val Background = Color(0xFF111113)
private val Card = Color(0xFF1C1C1F)
private val SecondaryFill = Color(0xFF2A2A2E)
private val SwitchTrack = Color(0xFF3A3A3E)

private val Border = white(alpha = 0.06f)
private val BorderInput = white(alpha = 0.08f)
private val BorderInputFocus = white(alpha = 0.20f)

private val Primary = Color(0xFF4A9EFF)
private val Ring = Primary.copy(alpha = 0.40f)
private val Destructive = Color(0xFFFF4D4D)
private val Success = Color(0xFF4ADE80)
private val Warning = Color(0xFFF59E0B)

private val HabitBlue = Color(0xFF4A9EFF)
private val HabitGreen = Color(0xFF76C442)
private val HabitOrange = Color(0xFFFF7A35)
private val HabitPurple = Color(0xFF8B5CF6)
private val ChartAmber = Color(0xFFF59E0B)
private val NavGrey = Color(0xFF888888)

/** `--secondary-foreground`: the one foreground that is neither pure white nor on the §1.5 ramp. */
private val OnSecondaryFill = white(alpha = 0.70f)

// ---------------------------------------------------------------------------------------------
// Material 3 colour scheme
// ---------------------------------------------------------------------------------------------

/**
 * The dark scheme — the only palette the design actually defines (`docs/tokens.md §1.1`).
 *
 * Two mappings are worth knowing before you use them:
 *
 * - **`surface` is the app background (`#111113`); cards are `surfaceContainer` (`#1c1c1f`).**
 *   The design has exactly two elevations and no tonal ramp between them, so the remaining
 *   `surfaceContainer*` slots are pinned to the nearest real token rather than interpolated.
 * - **`surfaceTint` is transparent.** Material 3 tints surfaces with it as elevation rises; this
 *   design is flat, so a raised `Surface` must stay `#1c1c1f` instead of drifting blue.
 *
 * The `*Fixed` slots (`primaryFixed`, `onPrimaryFixedVariant`, ...) are deliberately left at their
 * defaults: they exist for adaptive layouts this app does not build, nothing reads them, and
 * inventing values for them would be design we have not been given.
 */
val HabitsDarkColorScheme: ColorScheme =
    darkColorScheme(
        primary = Primary,
        onPrimary = Color.White,
        primaryContainer = SecondaryFill,
        onPrimaryContainer = Color.White,
        inversePrimary = Primary,
        secondary = SecondaryFill,
        onSecondary = OnSecondaryFill,
        secondaryContainer = SecondaryFill,
        onSecondaryContainer = OnSecondaryFill,
        // The prototype sets `--accent` to the same value as `--primary`; there is no third accent.
        tertiary = Primary,
        onTertiary = Color.White,
        tertiaryContainer = SecondaryFill,
        onTertiaryContainer = Color.White,
        background = Background,
        onBackground = Color.White,
        surface = Background,
        onSurface = Color.White,
        surfaceVariant = SecondaryFill,
        onSurfaceVariant = OnSecondaryFill,
        surfaceTint = Color.Transparent,
        // Snackbars default to these two. Mapping them onto the card makes an undesigned component
        // (the M6.6 undo snackbar) land on the design's own surface for free.
        inverseSurface = Card,
        inverseOnSurface = Color.White,
        error = Destructive,
        onError = Color.White,
        errorContainer = Card,
        onErrorContainer = Destructive,
        // `border` is the hairline on every card and the nav divider; `borderInput` is heavier and
        // only ever outlines a text field. Material's plain/variant split lines up with that.
        outline = BorderInput,
        outlineVariant = Border,
        scrim = Color.Black,
        surfaceBright = SecondaryFill,
        surfaceDim = Background,
        surfaceContainerLowest = Background,
        surfaceContainerLow = Card,
        surfaceContainer = Card,
        surfaceContainerHigh = SecondaryFill,
        surfaceContainerHighest = SwitchTrack,
    )

/**
 * v1 is dark-only regardless of the system setting (ADR-009), so there is no light palette to port
 * — the prototype never drew one.
 *
 * This exists so the theme's light/dark *structure* is already wired: `HabitsTheme` (M2.4) picks
 * between two named schemes and every call site reads `MaterialTheme.colorScheme`. Designing a
 * real light palette post-v1 then means replacing this one expression, not refactoring the app.
 * **It is not a designed light theme — do not ship a light mode on the strength of it.**
 */
val HabitsLightColorScheme: ColorScheme = HabitsDarkColorScheme

// ---------------------------------------------------------------------------------------------
// Extended scheme — the tokens Material 3 has no slot for
// ---------------------------------------------------------------------------------------------

/**
 * The habit palette — `docs/tokens.md §1.3`.
 *
 * A habit's colour drives its icon tint, dots, pills, chips, stat icons and chart gradient. The
 * user's Notion database has no colour property at all (`docs/notion-schema.md`), so the colour is
 * app-local and assigned from [assignable] (M7.3).
 */
@Immutable
data class HabitPalette(
    val blue: Color,
    val green: Color,
    val orange: Color,
    val purple: Color,
    val amber: Color,
    val grey: Color,
) {
    /**
     * The four colours the prototype actually gives to habits, in the order it uses them.
     *
     * [amber] is `--chart-5` and no habit uses it; [grey] tints the Settings tab icon and is not a
     * habit colour at all. Both are ported because they appear in the design, not to be assigned.
     */
    val assignable: List<Color> get() = listOf(blue, green, orange, purple)

    /** The fallback when a habit has no colour yet — `--primary` (`docs/tokens.md §1.2`). */
    val default: Color get() = blue
}

/**
 * The habit-colour alpha ramp — `docs/tokens.md §1.4`.
 *
 * These are alphas, not colours: the prototype tints by appending a hex alpha byte to whatever
 * colour the habit carries (`#4A9EFF` + `28`), so there is nothing to resolve until a habit is in
 * hand. Apply as `habitColor.copy(alpha = tint.iconCircle)`.
 *
 * Written as the original hex byte over 255 so each value stays traceable to the prototype rather
 * than to a rounded decimal in a table.
 */
@Immutable
data class HabitTintAlphas(
    /** `28`, ~0.157 — the circle behind a habit's emoji in `HabitIcon`. */
    val iconCircle: Float = 0x28 / OPAQUE_BYTE,
    /** `26`, ~0.149 — an incomplete day in `MonthDotGrid`. */
    val monthDotIncomplete: Float = 0x26 / OPAQUE_BYTE,
    /** `22`, ~0.133 — an incomplete day in `YearDotGrid`, and the stat-tile icon chip. */
    val yearDotIncomplete: Float = 0x22 / OPAQUE_BYTE,
    /** `20`, ~0.125 — an incomplete day in `WeekPills`, and an unselected habit chip. */
    val weekPillIncomplete: Float = 0x20 / OPAQUE_BYTE,
) {
    private companion object {
        const val OPAQUE_BYTE = 255f
    }
}

/**
 * The white text-opacity ramp — `docs/tokens.md §1.5`.
 *
 * Every foreground in the design is [base] at one of nine opacities, so this is one colour with
 * nine named levels rather than nine unrelated tokens: a light theme post-v1 changes [base] and
 * nothing else. Each name is the level's dominant use; the rest are listed alongside it.
 */
@Immutable
class TextEmphasis(
    private val base: Color,
) {
    /** 100% — headings, habit names, stat values, completion %. */
    val primary: Color = base

    /** 55% — the Notion schema property chips on the Settings screen. */
    val propertyChip: Color = base.copy(alpha = 0.55f)

    /** 50% — the "Not connected" label beside the connection status dot. */
    val statusLabel: Color = base.copy(alpha = 0.50f)

    /** 40% — the date label between the Month/Week nav arrows. */
    val navLabel: Color = base.copy(alpha = 0.40f)

    /** 35% — habit description (Week/Year), field labels, and the nav-arrow glyphs themselves. */
    val secondary: Color = base.copy(alpha = 0.35f)

    /** 30% — habit description (Month) and stat-tile labels. */
    val tertiary: Color = base.copy(alpha = 0.30f)

    /** 25% — section eyebrows, weekday initials, sub-captions. */
    val eyebrow: Color = base.copy(alpha = 0.25f)

    /** 22% — field helper text, chart axis ticks, and an inactive bottom-nav item. */
    val helper: Color = base.copy(alpha = 0.22f)

    /** 15% — input placeholders. */
    val placeholder: Color = base.copy(alpha = 0.15f)
}

/**
 * Per-state opacity for a day cell — `docs/design-spec.md §2`, `§3` and `§4`.
 *
 * These stack on top of the [HabitTintAlphas] fill rather than replacing it: an incomplete Month day
 * is the habit colour at 14.9% alpha, drawn at 30% opacity, for an effective 4.5%. The prototype
 * expresses that as a hex alpha byte plus a CSS `opacity`, and the two are kept separate here so
 * each still traces back to its source.
 *
 * A plain object rather than part of the composition local, following [Spacing] and [Sizing]: these
 * are the same in every theme, so there is nothing for a local to vary.
 */
object DayOpacity {
    /** An incomplete day in `MonthDotGrid`. */
    const val MONTH_INCOMPLETE = 0.30f

    /** A Month day that is in the future, or before the habit started. */
    const val MONTH_INACTIVE = 0.10f

    /** An incomplete day in `YearDotGrid`. */
    const val YEAR_INCOMPLETE = 0.28f

    /** A Year day before the habit started. The faintest mark the design draws. */
    const val YEAR_INACTIVE = 0.06f

    /** A week pill for a day that has not happened yet, complete or not. */
    const val WEEK_FUTURE = 0.30f
}

/**
 * The monthly chart's vertical fill — `docs/design-spec.md §5.4`.
 *
 * A `linearGradient` from the habit colour at 40% down to the same colour at zero, which is the one
 * place in the app where a colour fades rather than sitting at a fixed tint. Both stops are named
 * even though the bottom one is transparent: `Brush.verticalGradient` needs two colours, and a
 * literal `Color.Transparent` there would drop the hue and let some renderers fade towards black.
 *
 * A plain object beside [DayOpacity], for the same reason — one theme, nothing for a local to vary.
 */
object ChartOpacity {
    /** The fill directly under the curve. */
    const val FILL_TOP = 0.40f

    /** The fill at the axis. */
    const val FILL_BOTTOM = 0.00f
}

/** Focus affordances — `docs/tokens.md §1.1` (`borderInputFocus`) and `§1.2` (`ring`). */
@Immutable
data class FocusColors(
    /** The glow around a focused control. */
    val ring: Color,
    /** A focused text field's own border, replacing the resting `outline`. */
    val border: Color,
)

/**
 * Everything in `docs/tokens.md §1` that Material 3 has no slot for.
 *
 * Read it through `LocalHabitsColors.current`; M2.4 adds a `HabitsTheme` accessor beside
 * `MaterialTheme.colorScheme`. Anything Material *does* have a slot for lives in
 * [HabitsDarkColorScheme] instead — this is a supplement, never a parallel palette.
 */
@Immutable
data class HabitsColors(
    val habit: HabitPalette,
    val tint: HabitTintAlphas,
    val text: TextEmphasis,
    val focus: FocusColors,
    /** The connected status dot. Material has no success slot, only `error`. */
    val success: Color,
    /** The warning banner's text and border. */
    val warning: Color,
)

/** The extended scheme that accompanies [HabitsDarkColorScheme]. */
val HabitsDarkColors: HabitsColors =
    HabitsColors(
        habit =
            HabitPalette(
                blue = HabitBlue,
                green = HabitGreen,
                orange = HabitOrange,
                purple = HabitPurple,
                amber = ChartAmber,
                grey = NavGrey,
            ),
        tint = HabitTintAlphas(),
        text = TextEmphasis(base = Color.White),
        focus = FocusColors(ring = Ring, border = BorderInputFocus),
        success = Success,
        warning = Warning,
    )

/** The extended scheme that accompanies [HabitsLightColorScheme] — see the caveat there. */
val HabitsLightColors: HabitsColors = HabitsDarkColors

/**
 * Static because v1 never switches theme at runtime (ADR-009): a static local is read without
 * subscribing, so nothing recomposes on a value that cannot change.
 *
 * It defaults to the dark scheme rather than throwing, so a `@Preview` that forgets `HabitsTheme`
 * renders in the real palette instead of failing. That is safe only while dark is the only theme;
 * if a light palette ever ships, make this default an error instead.
 */
val LocalHabitsColors = staticCompositionLocalOf { HabitsDarkColors }
