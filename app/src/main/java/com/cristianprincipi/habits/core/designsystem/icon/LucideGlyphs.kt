package com.cristianprincipi.habits.core.designsystem.icon

/*
 * The thirteen Lucide glyphs the design uses. Source: `docs/tokens.md` section 5.
 *
 * **Generated, not hand-written.** Every string below is the `d` attribute of a path in the
 * matching `lucide-icons/lucide` SVG, taken verbatim. Those SVGs also use `rect`, `circle` and
 * `line` elements, which neither `ImageVector` nor an Android vector drawable understands, so
 * those were converted mechanically: a rect becomes a move plus four sides with arc corners, a
 * circle becomes two half-arcs, a line becomes a move and a line. Nothing was redrawn by hand and
 * no coordinate was rounded.
 *
 * All thirteen come from one family, deliberately, per ADR-024. Lucide is ISC; the licence ships
 * at `assets/licences/Lucide-ISC.txt` and is recorded in `docs/attributions.md`.
 *
 * Coordinates are in Lucide's own 24x24 viewport, which is what makes the stroke widths in
 * [LucideStroke] mean the same thing here that they mean in the prototype.
 */

/** One glyph: its Lucide name, and its path data in a 24x24 viewport. */
data class LucideGlyph(
    val name: String,
    val pathData: List<String>,
)

/** The thirteen glyphs, named for the role each plays in the design. */
object Lucide {
    /** Bottom nav, Month. */
    val LayoutGrid =
        LucideGlyph(
            name = "layout-grid",
            pathData =
                listOf(
                    "M4 3H9A1 1 0 0 1 10 4V9A1 1 0 0 1 9 10H4A1 1 0 0 1 3 9V4A1 1 0 0 1 4 3Z",
                    "M15 3H20A1 1 0 0 1 21 4V9A1 1 0 0 1 20 10H15A1 1 0 0 1 14 9V4A1 1 0 0 1 15 3Z",
                    "M15 14H20A1 1 0 0 1 21 15V20A1 1 0 0 1 20 21H15A1 1 0 0 1 14 20V15A1 1 0 0 1 15 14Z",
                    "M4 14H9A1 1 0 0 1 10 15V20A1 1 0 0 1 9 21H4A1 1 0 0 1 3 20V15A1 1 0 0 1 4 14Z",
                ),
        )

    /**
     * Bottom nav, Week.
     *
     * Lucide renamed this from `align-justify`, which is the name `docs/tokens.md` section 5
     * records and the one `lucide-react` exported when the prototype was built. Confirmed a
     * pure rename from Lucide's own alias metadata, so the geometry is still the prototype's.
     */
    val TextAlignJustify =
        LucideGlyph(
            name = "text-align-justify",
            pathData =
                listOf(
                    "M3 5h18",
                    "M3 12h18",
                    "M3 19h18",
                ),
        )

    /** Bottom nav, Year. */
    val Calendar =
        LucideGlyph(
            name = "calendar",
            pathData =
                listOf(
                    "M8 2v3",
                    "M16 2v3",
                    "M5 3H19A2 2 0 0 1 21 5V19A2 2 0 0 1 19 21H5A2 2 0 0 1 3 19V5A2 2 0 0 1 5 3Z",
                    "M3 9h18",
                ),
        )

    /**
     * Bottom nav, Stats.
     *
     * Lucide renamed this from `bar-chart-2`, which is the name `docs/tokens.md` section 5
     * records and the one `lucide-react` exported when the prototype was built. Confirmed a
     * pure rename from Lucide's own alias metadata, so the geometry is still the prototype's.
     */
    val ChartNoAxesColumn =
        LucideGlyph(
            name = "chart-no-axes-column",
            pathData =
                listOf(
                    "M5 21v-6",
                    "M12 21V3",
                    "M19 21V9",
                ),
        )

    /** Bottom nav, Settings. */
    val Settings =
        LucideGlyph(
            name = "settings",
            pathData =
                listOf(
                    "M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915",
                    "M9 12A3 3 0 1 0 15 12A3 3 0 1 0 9 12Z",
                ),
        )

    /** The header add button. */
    val Plus =
        LucideGlyph(
            name = "plus",
            pathData =
                listOf(
                    "M5 12h14",
                    "M12 5v14",
                ),
        )

    /** The Month/Week nav arrow, back. */
    val ChevronLeft =
        LucideGlyph(
            name = "chevron-left",
            pathData =
                listOf(
                    "m15 18-6-6 6-6",
                ),
        )

    /** The Month/Week nav arrow, forward. */
    val ChevronRight =
        LucideGlyph(
            name = "chevron-right",
            pathData =
                listOf(
                    "m9 18 6-6-6-6",
                ),
        )

    /** Stat tile, completed days. */
    val Hash =
        LucideGlyph(
            name = "hash",
            pathData =
                listOf(
                    "M4 9L20 9",
                    "M4 15L20 15",
                    "M10 3L8 21",
                    "M16 3L14 21",
                ),
        )

    /** Stat tile, completion rate. */
    val Percent =
        LucideGlyph(
            name = "percent",
            pathData =
                listOf(
                    "M19 5L5 19",
                    "M4 6.5A2.5 2.5 0 1 0 9 6.5A2.5 2.5 0 1 0 4 6.5Z",
                    "M15 17.5A2.5 2.5 0 1 0 20 17.5A2.5 2.5 0 1 0 15 17.5Z",
                ),
        )

    /** Stat tile, current streak. */
    val Flame =
        LucideGlyph(
            name = "flame",
            pathData =
                listOf(
                    "M12 3q1 4 4 6.5t3 5.5a1 1 0 0 1-14 0 5 5 0 0 1 1-3 1 1 0 0 0 5 0c0-2-1.5-3-1.5-5q0-2 2.5-4",
                ),
        )

    /** Stat tile, best streak. */
    val Trophy =
        LucideGlyph(
            name = "trophy",
            pathData =
                listOf(
                    "M10 14.66V17a1 1 0 0 1-1 1 2 2 0 0 0-2 2v2",
                    "M14 14.66V17a1 1 0 0 0 1 1 2 2 0 0 1 2 2v2",
                    "M17.916 10H19.5A2.5 2.5 0 0 0 22 7.5V5a1 1 0 0 0-1-1h-3",
                    "M4 22h16",
                    "M6 9a6 6 0 0 0 12 0V3a1 1 0 0 0-1-1H7a1 1 0 0 0-1 1z",
                    "M6.084 10H4.5A2.5 2.5 0 0 1 2 7.5V5a1 1 0 0 1 1-1h3",
                ),
        )

    /** The check inside a completed week pill. */
    val Check =
        LucideGlyph(
            name = "check",
            pathData =
                listOf(
                    "M20 6 9 17l-5-5",
                ),
        )

    /** Every glyph, for the M2.7 gallery and the render test. */
    val all: List<LucideGlyph>
        get() =
            listOf(
                LayoutGrid,
                TextAlignJustify,
                Calendar,
                ChartNoAxesColumn,
                Settings,
                Plus,
                ChevronLeft,
                ChevronRight,
                Hash,
                Percent,
                Flame,
                Trophy,
                Check,
            )
}
