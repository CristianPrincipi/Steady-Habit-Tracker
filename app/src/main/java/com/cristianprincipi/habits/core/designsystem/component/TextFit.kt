package com.cristianprincipi.habits.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import com.cristianprincipi.habits.core.designsystem.theme.Sizing

/*
 * M13.3 — two questions about whether text will fit, and they have different answers.
 *
 * At 200% font on a 320dp window, four places in the app rendered text like `Complete/d days`,
 * `Completio/n rate`, `Mo/n` and `Settin/gs`. That is not clipping and it is not an ellipsis: when a
 * single **word** is wider than the space it has, Compose breaks it wherever the character boundary
 * happens to fall. An ellipsis at least says "there is more"; `Completio` says nothing and reads as
 * a bug.
 *
 * ⚠⚠ **The two fixes below are not interchangeable, and choosing wrongly is the trap.** Content that
 * a screen reader announces — a stat tile's label, a habit's name — must be given **room**, because
 * shrinking it defeats the font scale the user asked for. Decoration whose meaning is carried
 * somewhere else may be **shrunk**, because the alternative is a word cut in half. Each caller says
 * in its own comment which of the two it is and why.
 *
 * ⚠ Both are measured rather than thresholded. A `Dp` typed by hand would be a guess about a font at
 * a scale, and would go stale the first time either changed; `rememberTextMeasurer` caches its
 * layouts, so asking is cheap enough to do in composition.
 */

/**
 * **Will Compose have to break a word to fit this text?** — the question that decides a *layout*.
 *
 * ⭐ **About words, not about the whole string.** `"Completion rate"` wrapping to two lines is
 * correct and wanted; `"Completion"` splitting in half is the failure, and the condition under which
 * Compose splits one is exactly this: the widest word does not fit. Asking it directly is what makes
 * a caller's layout decision self-maintaining — a copy change, a longer translation or a different
 * typeface all move the answer without anyone editing a threshold.
 *
 * @param available the width the text actually gets — the caller's own width minus its padding, not
 *   the width of the card around it.
 */
@Composable
fun everyWordFits(
    text: String,
    style: TextStyle,
    available: Dp,
): Boolean {
    val measurer = rememberTextMeasurer()
    val availablePx = with(LocalDensity.current) { available.roundToPx() }
    return remember(text, style, availablePx, measurer) {
        availablePx > 0 &&
            text
                .split(WORD_SEPARATORS)
                .filter { it.isNotEmpty() }
                .all { word -> measurer.measure(word, style).size.width <= availablePx }
    }
}

/**
 * **One font size at which every one of [texts] fits [available] on a single line** — the question
 * that decides a *size*.
 *
 * ⚠⚠ **One size for the whole set, and that is the entire reason this exists rather than
 * `BasicText(autoSize = …)`.** Compose's own auto-sizing is per-`Text`: pointed at a row of weekday
 * initials it shrank `Mon` and `Wed` and left `Tue`, `Thu`, `Fri`, `Sat` and `Sun` at full size, and
 * a heading row at two sizes reads as a rendering fault rather than as a design. Measuring the
 * widest member once and giving that size to all of them keeps the row uniform.
 *
 * ⚠ **Only for text whose meaning is carried elsewhere.** Shrinking is a way of *declining* part of
 * the font scale the user chose, and the two callers earn it: the `Mon…Sun` heading, whose semantics
 * M13.2 cleared because every pill announces its weekday in full, and the bottom nav's labels, whose
 * word is also the tab's accessible name and is repeated by its glyph and its position.
 *
 * ⭐ **And the floor is a *declared* size, so it scales too.** At 200% [Sizing.autoSizeFloor] draws
 * larger than either caller's design size does at 100% — the scaling is capped, never removed.
 *
 * Steps down rather than solving, because glyph advance is not quite linear in font size: hinting
 * and rounding make a computed answer wrong by a fraction that is exactly the fraction that matters.
 *
 * @return [style]'s own size whenever it already fits, so nothing moves at any scale where the
 *   design's size works. Never returns less than [floor]; past that the caller clips, which is the
 *   lesser evil against text too small to read.
 */
@Composable
fun fittedFontSize(
    texts: List<String>,
    style: TextStyle,
    available: Dp,
    floor: TextUnit = Sizing.autoSizeFloor,
): TextUnit {
    val measurer = rememberTextMeasurer()
    val availablePx = with(LocalDensity.current) { available.roundToPx() }
    val start = style.fontSize
    return remember(texts, style, availablePx, floor, measurer) {
        if (start.isUnspecified || availablePx <= 0) return@remember start

        var size = start
        while (size > floor) {
            val candidate = style.copy(fontSize = size)
            if (texts.all { measurer.measure(it, candidate).size.width <= availablePx }) break
            size = (size.value - STEP).sp
        }
        maxOf(size.value, floor.value).sp
    }
}

/** Whitespace, and the separators a wrappable string is allowed to break at anyway. */
private val WORD_SEPARATORS = Regex("[\\s ]+")

/** Half a point. Small enough that the step is invisible, large enough to bound the loop. */
private const val STEP = 0.5f
