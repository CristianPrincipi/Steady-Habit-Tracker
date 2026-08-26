package com.cristianprincipi.habits.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.designsystem.icon.Lucide
import com.cristianprincipi.habits.core.designsystem.icon.LucideGlyph
import com.cristianprincipi.habits.core.designsystem.theme.HabitsColors

/**
 * The five bottom-nav tabs, in the order the design draws them (`docs/design-spec.md` §1).
 *
 * A tab is a [TopLevelRoute] plus the three things the bar needs to draw it. The route stays free of
 * all of it — a destination is not a piece of styling — and the bar stays free of deciding what the
 * destinations are.
 *
 * **Declaration order is render order.** `entries` is what the bar iterates, so moving an entry
 * moves the tab. The order is Month, Week, Year, Stats, Settings even though **Week is the start
 * destination**: where the app opens and where the tab sits in the row are different questions, and
 * the design answers them differently.
 */
enum class HabitsTab(
    val route: TopLevelRoute,
    @field:StringRes val label: Int,
    val glyph: LucideGlyph,
) {
    MONTH(Month, R.string.tab_month, Lucide.LayoutGrid),
    WEEK(Week, R.string.tab_week, Lucide.TextAlignJustify),
    YEAR(Year, R.string.tab_year, Lucide.Calendar),
    STATS(Stats, R.string.tab_stats, Lucide.ChartNoAxesColumn),
    SETTINGS(Settings, R.string.tab_settings, Lucide.Settings),
    ;

    /**
     * The colour this tab shows **only while it is active** (`docs/design-spec.md` §1).
     *
     * Taken as a parameter rather than read from `HabitsTheme` so that this file stays a plain
     * table: a `@Composable` accessor here would make the tab list unreadable from a unit test, and
     * the mapping from tab to colour is exactly the kind of thing worth pinning without an emulator.
     */
    fun activeColor(colors: HabitsColors): Color =
        when (this) {
            MONTH -> colors.habit.blue
            WEEK -> colors.habit.green
            YEAR -> colors.habit.orange
            STATS -> colors.habit.purple
            SETTINGS -> colors.habit.grey
        }
}

/**
 * The tab that owns a destination.
 *
 * ⭐ **This `when` is the enforcement M5.1 promised.** It is exhaustive over [TopLevelRoute], so a
 * sixth top-level route does not compile until someone has decided what its tab looks like. A list
 * of tabs kept by hand would simply have gone stale, and the sixth destination would have shipped
 * with no way to reach it.
 */
fun TopLevelRoute.tab(): HabitsTab =
    when (this) {
        Month -> HabitsTab.MONTH
        Week -> HabitsTab.WEEK
        Year -> HabitsTab.YEAR
        Stats -> HabitsTab.STATS
        Settings -> HabitsTab.SETTINGS
    }
