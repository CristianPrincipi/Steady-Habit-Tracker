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

// M13.10 — raised 0.08 → 0.35 on the user's ruling. At 0.08 a text field's border was **1.26:1**
// against its card; WCAG 1.4.11 asks 3:1 for the boundary that identifies a control. 0.35 is the
// same figure `weekPillOutline` reaches 3.20:1 with, on the same card colour, and it lands on the
// three controls that share this token: the field, the radio and the checkbox. The two status dots
// that also used it moved to `dotInactive` instead — see its KDoc for why they must not follow.
private val BorderInput = white(alpha = 0.35f)
private val DotInactive = white(alpha = 0.08f)

// M13.10 — raised 0.20 → 0.70, and NOT as a design flourish: the line above took the resting border
// to 0.35, which left a focused field drawn **dimmer than an unfocused one**. That is a defect this
// step would have introduced. The design's own ratio was focus ≈ 2.5x resting; this keeps focus
// clearly the stronger of the two, which `ContrastTest` now asserts as a relationship rather than a
// number so it cannot invert again.
private val BorderInputFocus = white(alpha = 0.70f)

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
        // outlines the **controls** — the text field, the radio, the checkbox. Material's
        // plain/variant split lines up with that, and M13.10 is what made the distinction load-bearing
        // rather than decorative: 1.4.11 applies to one of these two and not the other.
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
    /**
     * M13.4 — the outline that makes an **unticked** week pill visible as a control.
     *
     * ⚠⚠ **Not a fill, and the arithmetic is why.** WCAG 1.4.11 wants 3:1 for the visual information
     * that identifies a component and its states, and at `weekPillIncomplete` an empty pill is
     * **1.21:1** against its card — very nearly invisible. Raising the *fill* to reach 3:1 needs
     * alpha **0.79 for purple**, which draws an unticked day at almost the ticked colour and
     * destroys the very state distinction the criterion is about. An outline buys the visibility
     * without touching the fill, which is what Material's own unchecked checkbox does.
     *
     * White rather than the habit colour, because the habit colour at full strength is already what
     * rings **today** ([todayRing]) — two habit-coloured outlines would make today stop reading as
     * today. 0.35 gives 3.20:1 on the card.
     */
    val weekPillOutline: Float = 0.35f,
) {
    private companion object {
        const val OPAQUE_BYTE = 255f
    }
}

/**
 * The white text-opacity ramp — `docs/tokens.md §1.5`, **re-spaced for WCAG AA at M13.4**.
 *
 * Every foreground in the design is [base] at one of nine opacities, so this is one colour with
 * nine named levels rather than nine unrelated tokens: a light theme post-v1 changes [base] and
 * nothing else. Each name is the level's dominant use; the rest are listed alongside it.
 *
 * ### ⚠⚠ These are NOT the prototype's opacities, and it is the design's one deliberate departure
 *
 * The design's ramp ran `1.00 · 0.55 · 0.50 · 0.40 · 0.35 · 0.30 · 0.25 · 0.22 · 0.15`, and **six of
 * those nine sat below WCAG AA** on this app's own background: 3.81, 3.19, 2.66, 2.20, 1.97 and 1.54
 * against the 4.5 that normal text requires. Every one is drawn at 9–14sp, so none reached the 3:1
 * large-text allowance either — 14sp SemiBold is not WCAG "large".
 *
 * ⚠ **A nine-step white-opacity ramp on near-black cannot meet AA by nudging.** The minimum alpha
 * for 4.5:1 over both `#111113` and `#1c1c1f` is `0.46`, so all six failing levels collapse onto one
 * value and nine levels of hierarchy become four.
 *
 * ⭐ **So the ramp is re-spaced by contrast RATIO rather than by alpha** — geometrically from 17:1
 * down to 4.55:1. That keeps **nine distinct, monotonic levels**, every one compliant, and the cost
 * is the design's low-contrast mood. The user was shown all three states rendered and chose this one
 * (ADR-111). ⚠ The alphas below are therefore *derived from a target ratio*, not typed: `ContrastTest`
 * is what keeps them honest, and it is what to re-run if the background ever moves.
 */
@Immutable
class TextEmphasis(
    private val base: Color,
) {
    /** 17:1 — headings, habit names, stat values, completion %. Unchanged. */
    val primary: Color = base

    /** 14.5:1 — the Notion schema property chips on the Settings screen. Was 55%. */
    val propertyChip: Color = base.copy(alpha = 0.9216f)

    /** 12.3:1 — the "Not connected" label beside the connection status dot. Was 50%. */
    val statusLabel: Color = base.copy(alpha = 0.8392f)

    /** 10.5:1 — the date label between the Month/Week nav arrows. Was 40%. */
    val navLabel: Color = base.copy(alpha = 0.7725f)

    /** 8.9:1 — habit description (Week/Year), field labels, and the nav-arrow glyphs themselves. Was 35%. */
    val secondary: Color = base.copy(alpha = 0.7020f)

    /** 7.6:1 — habit description (Month) and stat-tile labels. Was 30%. */
    val tertiary: Color = base.copy(alpha = 0.6392f)

    /** 6.4:1 — section eyebrows, weekday initials, sub-captions. Was 25%. */
    val eyebrow: Color = base.copy(alpha = 0.5765f)

    /** 5.5:1 — field helper text, chart axis ticks, and an inactive bottom-nav item. Was 22%. */
    val helper: Color = base.copy(alpha = 0.5176f)

    /** 4.6:1 — input placeholders. Was 15%. */
    val placeholder: Color = base.copy(alpha = 0.4627f)

    /**
     * 2.7:1 — the label on a **disabled** control, and the one level deliberately left below AA.
     *
     * ⚠⚠ **WCAG 1.4.3 exempts "inactive user interface components" by name**, and this is the only
     * place in the app that exemption is claimed. It exists because M13.4 created the need for it:
     * raising the ramp took the disabled state from *white against 30% white* to *white against 64%*,
     * and a button that no longer looks disabled is a worse accessibility outcome than a dim one —
     * the user cannot tell it will not respond.
     *
     * ⭐ So this is the design's old `tertiary`, kept at its original 30% for exactly the case the
     * standard sets aside. **Do not reach for it to make something quieter**; every level above is
     * compliant and one of them is the right answer for live content.
     */
    val disabled: Color = base.copy(alpha = 0.30f)
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
    /**
     * A status dot in its **off** state — not connected, or a wizard step not yet reached.
     *
     * ⭐ **Split out from `outline` in M13.10, and the split is the point.** Both used to be one
     * token at 0.08. When the user ruled that a text input's 1.26:1 border had to reach 3:1, raising
     * `outline` was the fix — and it would have dragged these two dots to 0.35 with it, brightening
     * an indicator the design puts at `white/20` (`docs/design-spec.md` §6.1) for a reason that has
     * nothing to do with the change.
     *
     * ⚠ **WCAG 1.4.11 does not apply here and that is why they can differ.** It governs the visual
     * information that identifies a **component** and its states; these are decorative dots sitting
     * beside text that already says "Not connected" in words. The controls had to move; these did not.
     */
    val dotInactive: Color,
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
        dotInactive = DotInactive,
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
