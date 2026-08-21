package com.cristianprincipi.habits.core.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Spacing and component dimensions, ported from the Figma Make prototype.
 *
 * Source of truth: `docs/tokens.md §4`. The prototype uses Tailwind's default scale, where one unit
 * is 4px, so every value here is a multiple of 4 except the three the designer set by hand.
 *
 * `CLAUDE.md §6` bans a raw `dp` anywhere outside this package, which is why component sizes live
 * here too rather than in the components that draw them ([Sizing]).
 */

/** Padding and gaps — `docs/tokens.md §4.1` (layout), `§4.2` (cards) and `§4.3` (grids). */
object Spacing {
    // --- §4.1 Layout ---------------------------------------------------------------------------

    /** `p-4` — the content padding of every scrollable view. */
    val screenPadding: Dp = 16.dp

    /** `px-5` — the header row and the nav-arrow row, both wider than the content below them. */
    val headerHorizontal: Dp = 20.dp

    /**
     * `pb-28` — bottom padding that keeps the last card clear of the floating bottom nav.
     *
     * This is scroll *content* padding, not a container inset: the nav bar floats above the list,
     * so the list must scroll past it rather than stop short of it.
     */
    val bottomNavClearance: Dp = 112.dp

    // --- §4.2 Cards ----------------------------------------------------------------------------

    /** `p-3.5` — the Month habit card only, which is tighter than every other card. */
    val cardPaddingCompact: Dp = 14.dp

    /** `p-4` — the Week, Year, stat and settings cards. */
    val cardPadding: Dp = 16.dp

    /** `space-y-3` / `gap-3` — between cards in a list or grid, and between sections inside a card. */
    val cardGap: Dp = 12.dp

    /** `space-y-4` — the Week card's internal spacing, and the gap between Stats sections. */
    val cardContentGap: Dp = 16.dp

    /** `space-y-5` — the gap between Settings sections, the loosest spacing in the app. */
    val settingsSectionGap: Dp = 20.dp

    // --- §4.3 Grids ----------------------------------------------------------------------------

    /** The gap between days in `YearDotGrid`. */
    val yearDotGap: Dp = 2.dp

    /** The gap between days in `MonthDotGrid`. */
    val monthDotGap: Dp = 3.dp

    /** `gap-1.5` — between the seven pills of a week row. */
    val weekPillGap: Dp = 6.dp

    /** `gap-2` — between habit chips in the Stats selector row. */
    val chipGap: Dp = 8.dp
}

/*
 * Not ported: `pt-10` (40px) header top padding. On Android that is the status-bar inset, which
 * varies by device and must be read from `WindowInsets` — `docs/tokens.md §7`, `CLAUDE.md §6`.
 * Hardcoding 40dp would put the header under the status bar on some devices and leave a gap on
 * others. There is deliberately no token for it.
 */

/**
 * Fixed component dimensions — `docs/tokens.md §4.4`.
 *
 * **Three of these are below Android's 48dp minimum touch target** and are marked as such. They
 * keep their drawn size — shrinking the design to satisfy the guideline would be the wrong fix —
 * and gain a larger target around them, via `Modifier.minimumInteractiveComponentSize()` or an
 * explicit [minimumTouchTarget] box with the drawn shape centred inside.
 */
object Sizing {
    /** `w-1 h-1` — the dot under an active bottom-nav item. */
    val navIndicatorDot: Dp = 4.dp

    /** One day in `YearDotGrid` on the Year card, where a full year has to fit. */
    val yearDotSmall: Dp = 4.dp

    /** `YearDotGrid`'s default day size. */
    val yearDot: Dp = 5.dp

    /** One day in `YearDotGrid` on the Stats "Year overview" card, which has more room. */
    val yearDotLarge: Dp = 6.dp

    /** `w-2 h-2` — the connection status dot on the Settings screen. */
    val statusDot: Dp = 8.dp

    /** `w-7 h-7` — the rounded chip holding a stat tile's icon. */
    val statTileIcon: Dp = 28.dp

    /** `w-8 h-8` — the habit icon on a Month card. */
    val habitIconSmall: Dp = 32.dp

    /** `w-8 h-8` — a Month/Week nav arrow. **Below the touch minimum**; see [minimumTouchTarget]. */
    val navArrowButton: Dp = 32.dp

    /** `w-9 h-9` — the header `+` button. **Below the touch minimum**; see [minimumTouchTarget]. */
    val addButton: Dp = 36.dp

    /**
     * `h-9` — the drawn height of a week pill. **Below the touch minimum**, and the one that matters
     * most: check-off lives on these pills (ADR-009), so this is the app's primary tap target. The
     * drawn height stays 36dp and the target grows to [minimumTouchTarget].
     */
    val weekPillHeight: Dp = 36.dp

    /** `w-10 h-10` — the habit icon on a Week or Year card. */
    val habitIconLarge: Dp = 40.dp

    /** `border` — the hairline on every card. Tailwind's default border width. */
    val hairlineBorder: Dp = 1.dp

    /** `box-shadow: 0 0 0 1.5px` — the ring the design puts around today's week pill. */
    val todayRing: Dp = 1.5.dp

    // --- Icon sizes, docs/tokens.md §5 -------------------------------------------------------
    //
    // A three-step ladder rather than one name per role: the design draws icons at exactly three
    // sizes, and naming them by size keeps a glyph from acquiring a second name the day it is
    // reused. The STROKE widths that go with them are not dp and live in `LucideStroke`.

    /** The four stat-tile icons, and the check inside a completed week pill. */
    val iconSmall: Dp = 13.dp

    /** The header `+` button, and the Month/Week nav chevrons. */
    val iconMedium: Dp = 18.dp

    /** Every bottom-nav item. */
    val iconLarge: Dp = 22.dp

    /** Android's minimum interactive size. Not from the prototype — the web mock has no touch. */
    val minimumTouchTarget: Dp = 48.dp
}
