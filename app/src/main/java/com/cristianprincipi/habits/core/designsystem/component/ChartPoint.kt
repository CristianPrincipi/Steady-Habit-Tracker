package com.cristianprincipi.habits.core.designsystem.component

import androidx.compose.runtime.Immutable

/**
 * One point of a [MonthlyChart]: a value, and the word under it.
 *
 * The label arrives already formatted, as `StatTile`'s value does and for the same reason — turning
 * a `YearMonth` into `Sep` needs a locale, and the design system stays out of that. The value stays
 * a number because the geometry needs it.
 *
 * Its own file beside `DayStatus.kt`, and for the same reason: a type the whole app may pass into a
 * component reads better where nothing else competes with it.
 */
@Immutable
data class ChartPoint(
    val label: String,
    val value: Int,
)
