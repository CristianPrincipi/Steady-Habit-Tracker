package com.cristianprincipi.habits.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Corner radii, ported from the Figma Make prototype.
 *
 * Source of truth: `docs/tokens.md §3`.
 *
 * **The trap in this file is 14, not 12.** `theme.css` sets `--radius: 10px` and remaps Tailwind's
 * `sm`/`md`/`lg`/`xl` through it — but it never remaps `2xl`. So `rounded-xl` is `10 + 4` = **14dp**
 * on pills, inputs and buttons, while `rounded-2xl` keeps Tailwind's untouched **16dp** on cards.
 * Reading `rounded-xl` as Tailwind's stock 12px is the single easiest mistake to make here, and it
 * would be wrong on every interactive element in the app.
 */

/**
 * The design's five radii, mapped onto Material 3's five shape slots in ascending order.
 *
 * Those slot names are **size tiers, not roles** — `medium` means "the middle radius", not "the one
 * cards use". The design's radii happen to land on the five slots exactly, which is why this maps
 * so cleanly, but it does mean a stock Material component's default shape is whatever tier its own
 * spec reaches for and not necessarily the tier this design would pick. Pass `shape =` explicitly
 * when using one; our own components in M2.5 name the tier they want.
 *
 * Material 3 1.4.0 carries three further slots (`largeIncreased`, `extraLargeIncreased`,
 * `extraExtraLarge`), all still `internal` — unreachable from here and unreadable by our code, so
 * there is nothing to fill.
 */
val HabitsShapes: Shapes =
    Shapes(
        // `rounded-sm`, 10 - 4. Unused by any screen in the prototype; kept for the ladder.
        extraSmall = RoundedCornerShape(6.dp),
        // `rounded-md`, 10 - 2 — the Notion schema property chips.
        small = RoundedCornerShape(8.dp),
        // `rounded-lg`, `--radius` itself — the stat-tile icon chip.
        medium = RoundedCornerShape(10.dp),
        // `rounded-xl`, 10 + 4 — week pills, nav arrows, inputs, the Connect button, banners.
        large = RoundedCornerShape(14.dp),
        // `rounded-2xl`, Tailwind's default and deliberately NOT remapped — every card and tile.
        extraLarge = RoundedCornerShape(16.dp),
    )

/**
 * The radii Material 3 has no slot for.
 *
 * The two dot radii are exposed as [Dp] as well as as shapes because both dot grids are drawn on a
 * `Canvas` (`docs/design-spec.md §2`, §4) — a 365-cell year grid is far too many composables to
 * emit individually, and `drawRoundRect` takes a `CornerRadius`, not a `Shape`.
 */
object HabitsCorners {
    /** `rounded-[3px]` — one day in `MonthDotGrid`. */
    val monthDot: Dp = 3.dp

    /** `borderRadius: 1`, set inline in the prototype — one day in `YearDotGrid`. */
    val yearDot: Dp = 1.dp

    val monthDotShape: Shape = RoundedCornerShape(monthDot)

    val yearDotShape: Shape = RoundedCornerShape(yearDot)

    /**
     * `rounded-full` — habit icons, the `+` button, habit chips, the connection status dot and the
     * bottom-nav active indicator.
     */
    val pill: Shape = CircleShape
}

/*
 * Not ported: `sm:rounded-[40px]`. That is the simulated phone bezel the prototype draws around
 * itself in a browser window (`docs/tokens.md §7`). Android is the phone.
 */
