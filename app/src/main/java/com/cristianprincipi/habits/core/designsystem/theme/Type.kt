package com.cristianprincipi.habits.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.cristianprincipi.habits.R

/*
 * Type, ported from the Figma Make prototype.
 *
 * Source of truth: `docs/tokens.md §2`. Sizes come from §2.1, weights from §2.2, and the tracking
 * and tabular-figure treatments from §2.3.
 *
 * Both families are bundled as `res/font` resources rather than fetched: `CLAUDE.md §8` allows the
 * app to contact `api.notion.com` and nothing else, so the prototype's Google Fonts `@import` has
 * no Android equivalent. Both are SIL OFL 1.1; the licences ship in `assets/licences/` and are
 * recorded in `docs/attributions.md`.
 */

// ---------------------------------------------------------------------------------------------
// Families
// ---------------------------------------------------------------------------------------------

/**
 * DM Sans at a given optical size.
 *
 * The bundled file is the **variable** font — one 240 KB resource carrying `wght` 100..1000 and
 * `opsz` 9..40 — so every weight below costs bytes only once.
 *
 * The optical-size axis is why this is a function rather than a single family. `opsz` defaults to
 * **9**, the low end of the axis: left alone, a 30sp headline would render with the design drawn
 * for 9pt text. Browsers avoid that with `font-optical-sizing: auto`, which the prototype inherits
 * by default, so each style here asks for the optical size that matches its own font size.
 *
 * The consequence to know about: a style's optical size is baked into its family, so overriding
 * `fontSize` on an existing style does **not** move `opsz` with it. Build a new style instead of
 * copying one to a very different size.
 */
private fun dmSans(opticalSize: TextUnit): FontFamily =
    FontFamily(
        dmSansFont(FontWeight.Light, opticalSize),
        dmSansFont(FontWeight.Normal, opticalSize),
        dmSansFont(FontWeight.Medium, opticalSize),
        dmSansFont(FontWeight.SemiBold, opticalSize),
        dmSansFont(FontWeight.Bold, opticalSize),
    )

private fun dmSansFont(
    weight: FontWeight,
    opticalSize: TextUnit,
) = Font(
    resId = R.font.dm_sans,
    weight = weight,
    variationSettings =
        FontVariation.Settings(
            FontVariation.weight(weight.weight),
            FontVariation.opticalSizing(opticalSize),
        ),
)

/**
 * DM Mono — static faces, and the only family in the app with fixed-width digits.
 *
 * The prototype uses it in exactly three places, all at weight 400: the integration-token field,
 * the database-ID field, and the schema property chips (`docs/tokens.md §2`). Medium is bundled
 * because the prototype's `@import` loads it, and it is 50 KB.
 */
private val DmMono =
    FontFamily(
        Font(resId = R.font.dm_mono_regular, weight = FontWeight.Normal),
        Font(resId = R.font.dm_mono_medium, weight = FontWeight.Medium),
    )

// ---------------------------------------------------------------------------------------------
// Tracking — docs/tokens.md §2.3. Expressed in em, exactly as the CSS does, so a value stays
// correct if the style it belongs to is ever resized.
// ---------------------------------------------------------------------------------------------

private val TrackingTight = (-0.025).em
private val TrackingWide = 0.025.em
private val TrackingWidest = 0.1.em

// ---------------------------------------------------------------------------------------------
// Material 3 typography
// ---------------------------------------------------------------------------------------------

/**
 * The design's scale, mapped onto Material 3's fifteen slots.
 *
 * Every slot is filled. Material's own defaults are Roboto, so a slot left alone is not "unused" —
 * it is a stock component silently rendering in the wrong typeface the first time one is used.
 *
 * The design tops out at 30sp, which is why all three `display*` slots carry the same style: there
 * is no larger tier to invent. Line heights come from Tailwind's own scale (12/16, 14/20, 16/24,
 * 20/28, 24/32, 30/36); the 9sp and 10sp sizes are arbitrary values in the prototype and set no
 * line height at all, so theirs continue Tailwind's ratio downwards.
 */
val HabitsTypography: Typography =
    Typography(
        displayLarge = statValueStyle(),
        displayMedium = statValueStyle(),
        displaySmall = statValueStyle(),
        headlineLarge = screenTitleStyle(),
        headlineMedium = screenTitleStyle(),
        headlineSmall = screenTitleStyle(),
        titleLarge = sansStyle(20.sp, 28.sp, FontWeight.SemiBold),
        titleMedium = sansStyle(16.sp, 24.sp, FontWeight.SemiBold),
        titleSmall = sansStyle(14.sp, 20.sp, FontWeight.SemiBold),
        bodyLarge = sansStyle(16.sp, 24.sp, FontWeight.Normal),
        bodyMedium = sansStyle(14.sp, 20.sp, FontWeight.Normal),
        bodySmall = sansStyle(12.sp, 16.sp, FontWeight.Normal),
        // Buttons, chips and bottom-nav labels — all semibold in the design (docs/tokens.md §2.2).
        labelLarge = sansStyle(14.sp, 20.sp, FontWeight.SemiBold),
        labelMedium = sansStyle(12.sp, 16.sp, FontWeight.SemiBold),
        labelSmall = sansStyle(10.sp, 14.sp, FontWeight.SemiBold),
    )

private fun sansStyle(
    size: TextUnit,
    lineHeight: TextUnit,
    weight: FontWeight,
    letterSpacing: TextUnit = TextUnit.Unspecified,
) = TextStyle(
    fontFamily = dmSans(size),
    fontSize = size,
    lineHeight = lineHeight,
    fontWeight = weight,
    letterSpacing = letterSpacing,
)

/** 24sp/700, `tracking-tight` — the screen title on every view. */
private fun screenTitleStyle() = sansStyle(24.sp, 32.sp, FontWeight.Bold, TrackingTight)

/** 30sp/700 — the stat-tile value. See [HabitsTextStyles.statValue] on the digit widths. */
private fun statValueStyle() = sansStyle(30.sp, 36.sp, FontWeight.Bold)

// ---------------------------------------------------------------------------------------------
// Extended styles — the treatments Material 3 has no slot for
// ---------------------------------------------------------------------------------------------

/**
 * Type treatments from `docs/tokens.md §2.3` that no Material slot describes: the two uppercase
 * eyebrows, the tracked weekday initials, the numeric values, and the monospaced fields.
 *
 * Read it through `LocalHabitsTextStyles.current`. Anything Material *does* have a slot for lives
 * in [HabitsTypography] instead — this is a supplement, never a parallel scale.
 */
@Immutable
data class HabitsTextStyles(
    /** 10sp/600, uppercase, `tracking-widest` — every section eyebrow and field label. */
    val eyebrow: TextStyle,
    /** 9sp/600, uppercase, `tracking-widest` — the `COMPLETION` caption under the Year card's %. */
    val eyebrowSmall: TextStyle,
    /** 10sp/500, `tracking-wide` — the `Mon…Sun` initials above the week pills. */
    val weekdayInitial: TextStyle,
    /** 30sp/700 — the stat-tile value. */
    val statValue: TextStyle,
    /** 24sp/700 — the Year card's completion percentage. */
    val metricValue: TextStyle,
    /** 14sp/400 DM Mono — the integration-token and database-ID fields. */
    val mono: TextStyle,
    /** 10sp/400 DM Mono — the Notion schema property chips. */
    val monoSmall: TextStyle,
)

/**
 * The extended styles that accompany [HabitsTypography].
 *
 * **On the two numeric styles.** `docs/tokens.md §2.3` asks for tabular figures so digits stop
 * jittering as a value changes, and the prototype sets `font-variant-numeric: tabular-nums` to get
 * them. That does nothing here, and it does nothing in the prototype either: DM Sans 4.004 ships
 * no `tnum` feature — its GSUB and GPOS tables have no numeric variants at all — and its digits are
 * strongly proportional, `1` being 342 units against `0`'s 656. Both were read out of the bundled
 * binary, not assumed.
 *
 * So the feature is requested anyway and documented as inert: it costs nothing, it states the
 * intent, and it starts working the day the font gains the feature. Until then these values jitter
 * exactly as the prototype's do. [HabitsTextStyles.mono] is the escape hatch if that ever matters —
 * DM Mono's digits are all 600 units wide — but swapping a stat value to monospace is a visible
 * design change and is the user's call, not ours.
 */
val HabitsDarkTextStyles: HabitsTextStyles =
    HabitsTextStyles(
        eyebrow = sansStyle(10.sp, 14.sp, FontWeight.SemiBold, TrackingWidest),
        eyebrowSmall = sansStyle(9.sp, 12.sp, FontWeight.SemiBold, TrackingWidest),
        weekdayInitial = sansStyle(10.sp, 14.sp, FontWeight.Medium, TrackingWide),
        statValue = statValueStyle().copy(fontFeatureSettings = TABULAR_FIGURES),
        metricValue = sansStyle(24.sp, 32.sp, FontWeight.Bold).copy(fontFeatureSettings = TABULAR_FIGURES),
        mono = TextStyle(fontFamily = DmMono, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
        monoSmall =
            TextStyle(
                fontFamily = DmMono,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Normal,
            ),
    )

/** The OpenType tag CSS `font-variant-numeric: tabular-nums` resolves to. */
private const val TABULAR_FIGURES = "tnum"

/**
 * Static for the same reason as `LocalHabitsColors`: v1 has one theme, so nothing should recompose
 * on a value that cannot change. It defaults to the real styles so a `@Preview` that forgets
 * `HabitsTheme` still renders in DM Sans rather than the platform default.
 */
val LocalHabitsTextStyles = staticCompositionLocalOf { HabitsDarkTextStyles }
