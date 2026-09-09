package com.cristianprincipi.habits.feature.widget

/*
 * How a NUMBER is fitted into the badge an emoji was designed for (M15.6g).
 *
 * ⭐⭐ An emoji needs no fitting and that is why `WidgetChrome` had none: one glyph at 16sp is
 * about 16dp wide inside a 36dp circle, at every font scale Android offers, in every locale. A
 * number is not one glyph. `84.3` is four, `7.5` is three, and both grow with the user's font
 * scale against a circle that does not — so the size that reads well on the user's phone today is
 * the size that clips on a phone set to 130%.
 *
 * ⚠ Measured, not thresholded — `TextFit.kt`'s rule (ADR-110 §5), and the same reasoning: a `dp`
 * typed here would be a guess about a typeface at a scale, and would go stale the first time either
 * changed. This is the Glance half of that idea, and it has to be a second implementation because
 * `rememberTextMeasurer` needs a Compose UI composition and a Glance widget has none.
 */

/** The size the badge draws a number at when it fits — chosen so `84.3` clears the circle's curve. */
internal const val MAX_BADGE_SP = 13f

/**
 * ⚠ The floor, past which the caller lets it clip.
 *
 * Eight is not a round number chosen for comfort: at Android's 200% setting it still draws at 16sp,
 * which is larger than the 13sp a default-scale user reads. **The scaling is capped, never
 * removed** — `TextFit.kt`'s argument for why a floor is acceptable at all.
 */
internal const val MIN_BADGE_SP = 8f

/**
 * How much of the badge's diameter a single line of text may occupy.
 *
 * A square inscribed in a circle is 70.7% of its diameter; a line of digits is far shorter than it
 * is wide, so it sits in a wider chord than that square's — 80% clears the curve at cap height with
 * a visible margin either side, rather than letting `84.3` graze the edge.
 */
internal const val BADGE_TEXT_WIDTH_RATIO = 0.8f

private const val BADGE_SP_STEP = 0.5f

/**
 * The largest size from [max] down at which the text fits [availablePx].
 *
 * ⭐ **Returns [max] untouched whenever the text already fits**, so nothing moves at the size and
 * scale the design was drawn for — only a long value or a large font scale steps it down.
 *
 * @param availablePx the width the text actually gets, in device pixels. Device pixels because the
 *   circle is a `dp` and does not grow with the font scale, which is exactly the mismatch this
 *   function exists to absorb.
 * @param widthPxAt what the text would measure, in device pixels, drawn at a given size in sp. A
 *   lambda rather than a `Paint` so the rule is a JVM test rather than an instrumented one.
 */
internal fun fitBadgeSp(
    availablePx: Float,
    max: Float = MAX_BADGE_SP,
    min: Float = MIN_BADGE_SP,
    widthPxAt: (sp: Float) -> Float,
): Float {
    if (availablePx <= 0f) return min
    var size = max
    while (size > min && widthPxAt(size) > availablePx) {
        size -= BADGE_SP_STEP
    }
    return maxOf(size, min)
}
