package com.cristianprincipi.habits.domain.model

/**
 * What the user typed for a number, read as the number it is — M16.2b, ADR-156 §2.
 *
 * **`7,5` and `7.5` are the same number**, because a phone's decimal keyboard offers whichever
 * separator its locale uses, and the value has to mean the same thing either way. That is the whole
 * of the leniency, and it is deliberately narrow:
 *
 * - **One separator at most.** `1.234,5` is a thousands separator in one locale and nonsense in
 *   another; guessing which would store a weight a thousand times too large. A weight or a night's
 *   sleep never needs grouping, so anything with two separators is refused.
 * - **Decimal hours only** for a duration — the user's ruling (2026-10-16): `7.5` is seven and a
 *   half hours, as Notion stores it. `7:30` is not a number and is refused rather than guessed at.
 * - **A leading minus is allowed.** Nothing here knows what the column measures (`CLAUDE.md` §3.4),
 *   and a Notion number can be negative.
 *
 * Pure and total: `null` for anything that is not a finite number, never an exception.
 */
object ReadingInput {
    private val shape = Regex("""-?(\d+([.,]\d*)?|[.,]\d+)""")

    /** The number in [text], or `null` when it is not one — which is what disables Save. */
    fun parse(text: String): Double? {
        val trimmed = text.trim()
        if (!shape.matches(trimmed)) return null
        return trimmed.replace(',', '.').toDoubleOrNull()?.takeIf(Double::isFinite)
    }
}
