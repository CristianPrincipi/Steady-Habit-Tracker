package com.cristianprincipi.habits.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
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
    /**
     * 10sp/400 — the monthly chart's axis ticks, and the only 10sp text the design does not
     * embolden or track. `recharts` sets a `fontSize` on the tick and nothing else, so it inherits
     * the body weight while every other 10sp label in the app is a deliberate 500 or 600.
     */
    val axisLabel: TextStyle,
    /** The design's own numerals: DM Sans at the specified 700, digits that shift. */
    val sansNumerals: NumeralStyles,
    /** The alternative: DM Mono, digits that do not shift, capped at Medium 500. */
    val monoNumerals: NumeralStyles,
    /** 14sp/400 DM Mono — the integration-token and database-ID fields. */
    val mono: TextStyle,
    /** 10sp/400 DM Mono — the Notion schema property chips. */
    val monoSmall: TextStyle,
)

/**
 * The two numbers the design calls out as tabular: a stat-tile value and a completion percentage.
 *
 * Grouped so both can exist twice over while the choice between them is open — see
 * [DefaultHabitsTextStyles] for what that choice actually is, and `Plan.md` M2.7/M2.8 for where it
 * gets made. Once it is made, one of the two groups is deleted.
 */
@Immutable
data class NumeralStyles(
    /** 30sp — the stat-tile value, the largest text in the app. */
    val statValue: TextStyle,
    /** 24sp — the Year card's completion percentage. */
    val metricValue: TextStyle,
)

/**
 * The extended styles that accompany [HabitsTypography].
 *
 * There is no light counterpart and there never will be: a `TextStyle` here carries size, weight,
 * tracking and family, and no colour — so unlike the palette, type does not vary by theme. Colour
 * arrives at the call site from `MaterialTheme.colorScheme` or `HabitsTheme.colors`.
 *
 * **On the two numeric styles.** `docs/tokens.md §2.3` asks for tabular figures so digits stop
 * jittering as a value changes, and the prototype sets `font-variant-numeric: tabular-nums` to get
 * them. That does nothing here, and it does nothing in the prototype either: DM Sans 4.004 ships
 * no `tnum` feature — its GSUB and GPOS tables have no numeric variants at all — and its digits are
 * strongly proportional, `1` being 342 units against `0`'s 656. Both were read out of the bundled
 * binary, not assumed.
 *
 * So the feature is requested anyway and documented as inert: it costs nothing, it states the
 * intent, and it starts working the day the font gains the feature.
 *
 * **Both numeral sets are built, and the choice is the user's to make by eye (M2.8).** The framing
 * that mattered, and that this file originally got wrong: matching the prototype here reproduces a
 * *font failure*, not a design decision. `App.tsx` asks for `tabular-nums` on both numbers and
 * `docs/tokens.md §2.3` says to carry it over — the request was simply ignored by a font that
 * cannot honour it.
 *
 * Neither option is free:
 *
 * - [sansNumerals] keeps the specified 700 weight and the design's own typeface; digits shift as
 *   values change.
 * - [monoNumerals] holds every digit at 600 units so nothing shifts — but **DM Mono has no bold.**
 *   The family tops out at Medium 500 while the design specifies 700, so the hero stat value cannot
 *   be both tabular and bold. The design's "tabular" and its "700" are not jointly satisfiable.
 *
 * Worth weighing when judging: both numbers sit in fixed-size containers, so what moves is glyphs
 * inside a stable box, never the layout around them — and they change about once a day.
 */
val DefaultHabitsTextStyles: HabitsTextStyles =
    HabitsTextStyles(
        eyebrow = sansStyle(10.sp, 14.sp, FontWeight.SemiBold, TrackingWidest),
        eyebrowSmall = sansStyle(9.sp, 12.sp, FontWeight.SemiBold, TrackingWidest),
        weekdayInitial = sansStyle(10.sp, 14.sp, FontWeight.Medium, TrackingWide),
        axisLabel = sansStyle(10.sp, 14.sp, FontWeight.Normal),
        sansNumerals =
            NumeralStyles(
                statValue = statValueStyle().copy(fontFeatureSettings = TABULAR_FIGURES),
                metricValue = sansStyle(24.sp, 32.sp, FontWeight.Bold).copy(fontFeatureSettings = TABULAR_FIGURES),
            ),
        monoNumerals =
            NumeralStyles(
                statValue = monoNumeralStyle(30.sp, 36.sp),
                metricValue = monoNumeralStyle(24.sp, 32.sp),
            ),
        mono = TextStyle(fontFamily = DmMono, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
        monoSmall =
            TextStyle(
                fontFamily = DmMono,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Normal,
            ),
    )

/**
 * A numeric value in DM Mono, at the heaviest weight the family actually has.
 *
 * [FontWeight.Medium], not the design's 700, and deliberately not `Bold`: asking a family for a
 * weight it does not contain lets Compose synthesise one by smearing the glyphs, which on a
 * monospaced face looks worse than simply being lighter than specified. Better to be honestly
 * 500 than fake 700.
 */
private fun monoNumeralStyle(
    size: TextUnit,
    lineHeight: TextUnit,
) = TextStyle(
    fontFamily = DmMono,
    fontSize = size,
    lineHeight = lineHeight,
    fontWeight = FontWeight.Medium,
    fontSynthesis = FontSynthesis.None,
)

/** The OpenType tag CSS `font-variant-numeric: tabular-nums` resolves to. */
private const val TABULAR_FIGURES = "tnum"

/**
 * Static for the same reason as `LocalHabitsColors`: v1 has one theme, so nothing should recompose
 * on a value that cannot change. It defaults to the real styles so a `@Preview` that forgets
 * `HabitsTheme` still renders in DM Sans rather than the platform default.
 */
val LocalHabitsTextStyles = staticCompositionLocalOf { DefaultHabitsTextStyles }
