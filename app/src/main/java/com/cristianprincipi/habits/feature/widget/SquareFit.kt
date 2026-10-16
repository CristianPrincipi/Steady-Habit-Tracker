package com.cristianprincipi.habits.feature.widget

/*
 * How the habit widget's corner square is fitted to the card it is drawn on — M15.11, ADR-153.
 *
 * ⭐⭐ Out of the composable on purpose, like `GridFit.kt` and `BadgeFit.kt`: where the square goes is
 * the kind of claim only a human comparing home-screen screenshots could otherwise check.
 *
 * ⚠⚠ **The first square was one fixed size, and the user found it wrong on bigger widgets.** A 10dp
 * square 12dp from the edge, on every card. Where the grid's dots are small that reads as today's dot
 * drawn large; on a card ~180dp tall the dots reach their 12dp cap (`MAX_DOT_DP`) and the square was
 * *smaller than a single dot*, and tight in the corner.
 *
 * ⭐⭐ **The full size is the user's pick, not a formula's.** A first fix grew it to 18dp on the biggest
 * cards. On the phone the user chose the square their 2x2 widgets drew, for every bigger card too — so
 * [LARGE_SQUARE] is what those 148x148dp cards drew, and every bigger card draws the same.
 */

/** The square at one card size. All dp, measured from the card's right edge where it says so. */
internal data class SquareFit(
    val sizeDp: Float,
    /** From the card's right edge to the square's right edge. */
    val endDp: Float,
    val strokeDp: Float,
) {
    /** What both header lines keep clear on their right: up to the square, plus the badge's own gap. */
    val textEndInsetDp: Float get() = endDp + sizeDp + BADGE_GAP.value - CARD_PADDING.value

    /**
     * The tap target's width from the card's corner: everything right of the text, and
     * [TARGET_TEXT_OVERLAP_DP] into it — a tap on a name still opens the app.
     */
    val targetWidthDp: Float get() = CARD_PADDING.value + textEndInsetDp + TARGET_TEXT_OVERLAP_DP
}

/**
 * The square for a habit widget [cardWidthDp] × [cardHeightDp].
 *
 * ⭐ **It is today's dot, drawn large — so it grows with the grid's dot**: [SQUARE_PER_DOT] times it,
 * from [SMALL_SQUARE] up to [LARGE_SQUARE] and no further. Its distance from the edge and its outline
 * move with it by the same fraction. Nothing else in the header grows, so nothing else moves.
 *
 * ⚠ **The name outranks the square.** Growing it takes width from both text lines, so it grows only
 * while the text keeps [MIN_NAME_ROOM_DP]; a narrow card that is tall keeps the small square.
 *
 * ⭐ The user's 2x2 card (148x148dp: 8.67dp dots, a 148dp-wide header) meets **both** limits exactly —
 * 1.5 × 8.67 = 13dp, and the name keeps 56dp — so it draws [LARGE_SQUARE] with nothing tuned to it.
 */
internal fun fitTodaySquare(
    cardWidthDp: Float,
    cardHeightDp: Float,
): SquareFit {
    val span = LARGE_SQUARE.sizeDp - SMALL_SQUARE.sizeDp
    val dot = fitHabitWidgetGrid(cardWidthDp, cardHeightDp).dotDp
    val byDot = (dot * SQUARE_PER_DOT - SMALL_SQUARE.sizeDp) / span
    // The text keeps what is left of the card after the padding, the badge, its gap and the square's
    // inset; that inset grows by this much from SMALL_SQUARE to LARGE_SQUARE.
    val insetGrowth = LARGE_SQUARE.textEndInsetDp - SMALL_SQUARE.textEndInsetDp
    val textRoomAtSmall =
        cardWidthDp - CARD_PADDING.value * 2 - BADGE_SIZE.value - BADGE_GAP.value - SMALL_SQUARE.textEndInsetDp
    val byWidth = (textRoomAtSmall - MIN_NAME_ROOM_DP) / insetGrowth
    val t = minOf(byDot, byWidth).coerceIn(0f, 1f)
    return SquareFit(
        sizeDp = lerp(SMALL_SQUARE.sizeDp, LARGE_SQUARE.sizeDp, t),
        endDp = lerp(SMALL_SQUARE.endDp, LARGE_SQUARE.endDp, t),
        strokeDp = lerp(SMALL_SQUARE.strokeDp, LARGE_SQUARE.strokeDp, t),
    )
}

private fun lerp(
    from: Float,
    to: Float,
    t: Float,
): Float = from + (to - from) * t

/**
 * The first square (`cf6cb32`): 10dp, on the card's 12dp padding, with a 1.5dp outline — not 2, which
 * on 10dp leaves a 6dp hole and reads as a filled dot. Still what a short card draws, the picker's
 * preview among them, where the dots are ~4dp.
 */
internal val SMALL_SQUARE = SquareFit(sizeDp = 10f, endDp = CARD_PADDING.value, strokeDp = 1.5f)

/**
 * ⭐ **The user's choice (2026-10-16): the square their 2x2 widgets drew in `a497ec7`** — 12.8dp, 15.2dp
 * from the edge, a 1.69dp outline — rounded to 13, 15 and 1.7, under a pixel from what they pointed
 * at. Every card from 2x2 up draws this one.
 */
internal val LARGE_SQUARE = SquareFit(sizeDp = 13f, endDp = 15f, strokeDp = 1.7f)

/** How much bigger than a grid dot the square is drawn: 13dp is 1.5 of the 2x2's 8.67dp dots. */
private const val SQUARE_PER_DOT = 1.5f

/**
 * Room the header text keeps beside a growing square. A seven-letter name at 15sp bold measured ~51dp
 * on the phone (ADR-151 §8), so this is that name and a little air.
 */
internal const val MIN_NAME_ROOM_DP = 56f

/** How far the target reaches into the text — 2dp, the same as the first square's. */
private const val TARGET_TEXT_OVERLAP_DP = 2f
