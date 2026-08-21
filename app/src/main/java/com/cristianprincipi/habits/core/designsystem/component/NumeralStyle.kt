package com.cristianprincipi.habits.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme

/**
 * Which typeface a large number is set in — the open question from ADR-021, built both ways so it
 * can be judged by eye rather than argued about (`Plan.md` M2.7, M2.8).
 *
 * Neither is free. Once the user has chosen, the loser and this enum both get deleted.
 */
enum class NumeralStyle {
    /**
     * DM Sans at the design's specified 700. Digits are proportional — `1` is roughly half the
     * width of `0` — so they shift as a value changes.
     */
    SANS,

    /**
     * DM Mono, every digit 600 units wide, so nothing shifts. **DM Mono has no bold**: this renders
     * at Medium 500 against the design's 700, because faking a weight the family does not contain
     * looks worse on a monospaced face than being honestly lighter.
     */
    MONO,
}

/** The style this [NumeralStyle] resolves to for a stat-tile value. */
@Composable
internal fun NumeralStyle.statValue(): TextStyle =
    when (this) {
        NumeralStyle.SANS -> HabitsTheme.textStyles.sansNumerals.statValue
        NumeralStyle.MONO -> HabitsTheme.textStyles.monoNumerals.statValue
    }

/** The style this [NumeralStyle] resolves to for a completion percentage. */
@Composable
internal fun NumeralStyle.metricValue(): TextStyle =
    when (this) {
        NumeralStyle.SANS -> HabitsTheme.textStyles.sansNumerals.metricValue
        NumeralStyle.MONO -> HabitsTheme.textStyles.monoNumerals.metricValue
    }
