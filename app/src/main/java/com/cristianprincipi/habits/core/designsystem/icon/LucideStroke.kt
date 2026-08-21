package com.cristianprincipi.habits.core.designsystem.icon

/**
 * Stroke widths, in Lucide's 24-unit viewport — `docs/tokens.md §5`.
 *
 * These are the numbers `lucide-react` takes as its `strokeWidth` prop, so they mean exactly what
 * they mean in the prototype. **They are not dp.** A glyph drawn at 13dp with a stroke of 3 renders
 * a line about 1.6dp wide, because the stroke scales with the viewport — which is the whole reason
 * the icons are built this way rather than as fixed drawables.
 */
object LucideStroke {
    /** Lucide's own default, and what every icon uses unless the design says otherwise. */
    const val DEFAULT = 2f

    /** The selected bottom-nav item. */
    const val NAV_ACTIVE = 2.2f

    /** Every other bottom-nav item. */
    const val NAV_INACTIVE = 1.5f

    /** The check inside a completed week pill — the heaviest stroke in the design. */
    const val CHECK = 3f
}
