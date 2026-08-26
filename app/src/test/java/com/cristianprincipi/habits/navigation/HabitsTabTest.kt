package com.cristianprincipi.habits.navigation

import androidx.compose.ui.graphics.Color
import com.cristianprincipi.habits.core.designsystem.theme.HabitsDarkColors
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The tab table, against `docs/design-spec.md` §1.
 *
 * The hex literals below are the design's own, written out rather than read from the theme. A test
 * that asserted `HabitsTheme.colors.habit.blue == HabitsTheme.colors.habit.blue` would pass however
 * wrong the palette became; the point is to pin the bar to the *spec*, so the spec's numbers are
 * what appear here. This is the one place in the app where a raw colour belongs outside the theme
 * package (`CLAUDE.md` §6).
 */
class HabitsTabTest {
    @Test
    fun theTabsAreTheFiveTheDesignDrawsInTheOrderItDrawsThem() {
        assertEquals(
            listOf(Month, Week, Year, Stats, Settings),
            HabitsTab.entries.map(HabitsTab::route),
        )
    }

    @Test
    fun everyTabOwnsTheColourTheDesignGivesIt() {
        val colors = HabitsDarkColors
        assertEquals(Color(0xFF4A9EFF), HabitsTab.MONTH.activeColor(colors))
        assertEquals(Color(0xFF76C442), HabitsTab.WEEK.activeColor(colors))
        assertEquals(Color(0xFFFF7A35), HabitsTab.YEAR.activeColor(colors))
        assertEquals(Color(0xFF8B5CF6), HabitsTab.STATS.activeColor(colors))
        assertEquals(Color(0xFF888888), HabitsTab.SETTINGS.activeColor(colors))
    }

    @Test
    fun noTwoTabsShareAColour() {
        // The colour is the only thing distinguishing an active tab from an inactive one, so a
        // duplicate would make two tabs indistinguishable while selected.
        val colours = HabitsTab.entries.map { it.activeColor(HabitsDarkColors) }

        assertEquals(colours.size, colours.distinct().size)
    }

    @Test
    fun everyRouteResolvesBackToItsOwnTab() {
        // `TopLevelRoute.tab()` is exhaustive over the sealed interface, so the compiler already
        // guarantees every route has *a* tab. What it cannot guarantee is that the mapping is the
        // right way round — a copy-paste in that `when` would silently light the wrong tab.
        HabitsTab.entries.forEach { tab ->
            assertEquals(tab, tab.route.tab())
        }
    }
}
