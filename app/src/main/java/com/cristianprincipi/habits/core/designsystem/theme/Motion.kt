package com.cristianprincipi.habits.core.designsystem.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color

/*
 * Motion, ported from the Figma Make prototype — `docs/tokens.md §6`.
 *
 * The design's whole motion language is one line: "everything is a CSS transition; there is no
 * keyframe animation, no spring, no shared-element transition." `App.tsx` never overrides Tailwind's
 * defaults — there is no `duration-*`, no `ease-*` and no `cubic-bezier` anywhere in the prototype
 * or its CSS — so **the entire design is 150ms at `cubic-bezier(.4, 0, .2, 1)`**, and that is what
 * this file holds.
 *
 * `CLAUDE.md §6` bans a raw `dp` outside this package for the reason a raw `150` belongs here too: a
 * duration typed at a call site is a number the fifth screen gets wrong by 50ms.
 */

/** The design's one transition — `docs/tokens.md §6`. */
object Motion {
    /**
     * `transition-*` — Tailwind's default 150ms, un-overridden by the prototype.
     *
     * Short on purpose. This is the duration of a *state change* on something already on screen — a
     * pill filling in, a chip taking the selection, a button becoming pressable. A user who has just
     * tapped is waiting for it, so it has to be over before they notice waiting.
     */
    const val STATE_CHANGE_MILLIS: Int = 150

    /**
     * Tailwind's default transition curve, which is **bit-for-bit** Compose's [FastOutSlowInEasing].
     *
     * Both are `cubic-bezier(0.4, 0, 0.2, 1)`. That is a coincidence worth writing down rather than
     * relying on quietly: `MotionTest` samples this curve against a locally-built
     * `CubicBezierEasing(0.4f, 0f, 0.2f, 1f)`, so the port is pinned to the *numbers* the designer's
     * CSS uses and not to a name androidx could redefine.
     */
    val stateChangeEasing: Easing = FastOutSlowInEasing

    /** [STATE_CHANGE_MILLIS] of [stateChangeEasing], for a colour — the common case by far. */
    val colorStateChange: FiniteAnimationSpec<Color> =
        tween(durationMillis = STATE_CHANGE_MILLIS, easing = stateChangeEasing)

    /** [STATE_CHANGE_MILLIS] of [stateChangeEasing], for anything measured as a `Float`. */
    val floatStateChange: FiniteAnimationSpec<Float> =
        tween(durationMillis = STATE_CHANGE_MILLIS, easing = stateChangeEasing)
}

/*
 * ⚠ **There is deliberately no token here for a screen transition or a list animation**, and the
 * absence is the decision (M12.5, ADR-105).
 *
 * The prototype has neither. Its five views swap with a plain React re-render, and its lists are
 * re-rendered arrays — so there is no designed value to port, and inventing a duration would be
 * inventing design. Both of those cases therefore use the **platform's** own motion:
 * `slideIntoContainer`'s default spring for a pushed destination, and `Modifier.animateItem`'s
 * defaults for a list.
 *
 * So the app speaks two motion languages on purpose, and the rule that separates them is simple:
 * **what the design drew moves at 150ms; what the design never had moves the way Android moves.**
 *
 * ### The five lists, and why `animateItem` is free where it is used
 *
 * Week, Month, Year, the habit list and the Stats selector are all keyed by habit id, and that set
 * changes only when the user's **Notion schema** does (ADR-010) — a sync adopting a new column,
 * dropping one, or reordering them. Rare, and exactly the moment a silent jump is hardest to read.
 *
 * ⚠ It costs **nothing on a first draw**, which is what makes it safe on tabs the user re-enters
 * constantly: `LazyLayoutItemAnimator` gates appearance on
 * `previousIndex == -1 && previousKeyToIndexMap != null`, and that map is null on the first measure
 * pass. Checked in foundation 1.12.0's source rather than assumed — a list that faded itself in on
 * every visit to a tab would be a worse app than the one without the modifier.
 */
